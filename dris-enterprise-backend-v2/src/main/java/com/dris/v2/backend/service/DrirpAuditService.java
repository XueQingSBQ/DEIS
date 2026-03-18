package com.dris.v2.backend.service;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.dao.DrirpAuditLogDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DrirpAuditService {
    private static final Logger log = LoggerFactory.getLogger(DrirpAuditService.class);

    private final DrirpAuditLogDao drirpAuditLogDao;
    private final BackendV2Properties properties;

    public DrirpAuditService(DrirpAuditLogDao drirpAuditLogDao, BackendV2Properties properties) {
        this.drirpAuditLogDao = drirpAuditLogDao;
        this.properties = properties;
    }

    public void record(
            String category,
            String traceId,
            String userId,
            String path,
            int status,
            long durationMs,
            boolean success,
            String errorMessage
    ) {
        if (!isAuditEnabled()) {
            return;
        }
        String effectiveCategory = isBlank(category) ? "DRIRP" : category.trim();
        String effectiveTraceId = isBlank(traceId) ? "trace-" + System.nanoTime() : traceId.trim();
        try {
            drirpAuditLogDao.insert(
                    clip(effectiveTraceId, 64),
                    clip(blankIfNull(userId), 64),
                    "",
                    "DRIRP",
                    clip(effectiveCategory + " " + blankIfNull(path), 255),
                    status,
                    Math.max(0L, durationMs),
                    success,
                    clip(blankIfNull(errorMessage), 512)
            );
        } catch (Exception ex) {
            log.warn("failed to persist drirp audit, category={}, traceId={}, err={}", effectiveCategory, effectiveTraceId, ex.getMessage());
        }
    }

    private boolean isAuditEnabled() {
        BackendV2Properties.DrirpSecurityProperties security = properties == null ? null : properties.getDrirpSecurity();
        return security == null || security.isAuditEnabled();
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String out = value.trim();
        if (maxLen <= 0 || out.length() <= maxLen) {
            return out;
        }
        return out.substring(0, maxLen);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
