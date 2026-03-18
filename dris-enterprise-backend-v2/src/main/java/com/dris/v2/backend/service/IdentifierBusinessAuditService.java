package com.dris.v2.backend.service;

import com.dris.v2.backend.auth.CurrentUser;
import com.dris.v2.backend.auth.CurrentUserContext;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.BusinessAuditConfiguration;
import com.dris.v2.backend.dao.IdentifierHistoryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class IdentifierBusinessAuditService {
    private static final Logger log = LoggerFactory.getLogger(IdentifierBusinessAuditService.class);

    private final IdentifierHistoryDao identifierHistoryDao;
    private final BackendV2Properties properties;
    private final Executor auditExecutor;
    private final AtomicLong droppedEvents = new AtomicLong(0L);

    public IdentifierBusinessAuditService(
            IdentifierHistoryDao identifierHistoryDao,
            BackendV2Properties properties,
            @Qualifier(BusinessAuditConfiguration.IDENTIFIER_BUSINESS_AUDIT_EXECUTOR) Executor auditExecutor
    ) {
        this.identifierHistoryDao = identifierHistoryDao;
        this.properties = properties;
        this.auditExecutor = auditExecutor;
    }

    public void record(String identifier, String operation, boolean success, String message) {
        if (!isEnabled()) {
            return;
        }
        String effectiveIdentifier = clip(trim(identifier), 30);
        String effectiveOperation = clip(trim(operation), 64);
        if (isBlank(effectiveIdentifier) || isBlank(effectiveOperation)) {
            return;
        }
        CurrentUser currentUser = CurrentUserContext.get();
        String actorUserId = clip(currentUser == null ? "" : trim(currentUser.getUserId()), 64);
        String actorRole = clip(extractActorRole(currentUser), 64);
        String effectiveMessage = clip(trim(message), 512);
        Runnable task = new Runnable() {
            @Override
            public void run() {
                persist(effectiveIdentifier, effectiveOperation, actorUserId, actorRole, success, effectiveMessage);
            }
        };

        if (!isAsyncEnabled()) {
            task.run();
            return;
        }
        try {
            auditExecutor.execute(task);
        } catch (RejectedExecutionException ex) {
            long dropped = droppedEvents.incrementAndGet();
            if (dropped <= 3 || dropped % 100 == 0) {
                log.warn("identifier business audit queue is full, drop event operation={}, identifier={}, dropped={}",
                        effectiveOperation, effectiveIdentifier, Long.valueOf(dropped));
            }
        } catch (Exception ex) {
            log.warn("failed to submit identifier business audit task, operation={}, identifier={}, err={}",
                    effectiveOperation, effectiveIdentifier, ex.getMessage());
        }
    }

    private void persist(
            String identifier,
            String operation,
            String actorUserId,
            String actorRole,
            boolean success,
            String message
    ) {
        try {
            identifierHistoryDao.insert(identifier, operation, actorUserId, actorRole, success, message);
        } catch (Exception ex) {
            log.warn("failed to persist identifier business audit, operation={}, identifier={}, err={}",
                    operation, identifier, ex.getMessage());
        }
    }

    private boolean isEnabled() {
        BackendV2Properties.BusinessAuditProperties audit = properties == null ? null : properties.getBusinessAudit();
        return audit != null && audit.isEnabled();
    }

    private boolean isAsyncEnabled() {
        BackendV2Properties.BusinessAuditProperties audit = properties == null ? null : properties.getBusinessAudit();
        return audit == null || audit.isAsyncEnabled();
    }

    private static String extractActorRole(CurrentUser currentUser) {
        if (currentUser == null) {
            return "";
        }
        if (!isBlank(currentUser.getUsername())) {
            return currentUser.getUsername();
        }
        if (!isBlank(currentUser.getOrgCode())) {
            return currentUser.getOrgCode();
        }
        return "";
    }

    private static String clip(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (maxLen <= 0 || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
