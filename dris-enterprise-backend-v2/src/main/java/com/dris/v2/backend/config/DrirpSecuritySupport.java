package com.dris.v2.backend.config;

import com.dris.v2.backend.service.DrirpAuditService;
import org.bdware.drirp.auth.DrirpAuthProvider;
import org.bdware.drirp.auth.HmacSha256AuthProvider;
import org.bdware.drirp.client.DrirpClientBuilder;
import org.bdware.drirp.filter.DrirpAuthFilter;
import org.bdware.drirp.filter.DrirpReplayProtectionFilter;
import org.bdware.drirp.filter.DrirpRequestFilter;
import org.bdware.drirp.model.DrirpMessage;
import org.bdware.drirp.server.DrirpDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DrirpSecuritySupport {
    private static final Logger log = LoggerFactory.getLogger(DrirpSecuritySupport.class);

    private final BackendV2Properties properties;
    private final DrirpAuditService drirpAuditService;

    public DrirpSecuritySupport(BackendV2Properties properties, DrirpAuditService drirpAuditService) {
        this.properties = properties;
        this.drirpAuditService = drirpAuditService;
    }

    public DrirpClientBuilder applyClientSecurity(DrirpClientBuilder builder) {
        if (builder == null) {
            return null;
        }
        if (!isEnabled()) {
            return builder;
        }
        DrirpAuthProvider authProvider = buildAuthProvider();
        if (authProvider == null) {
            return builder;
        }
        return builder
                .authProvider(authProvider)
                .requireResponseAuth(resolveSecurity().isRequireResponseAuth());
    }

    public void applyServerSecurity(DrirpDispatcher dispatcher) {
        if (dispatcher == null || !isEnabled()) {
            return;
        }
        DrirpAuthProvider authProvider = buildAuthProvider();
        if (authProvider == null) {
            return;
        }
        BackendV2Properties.DrirpSecurityProperties security = resolveSecurity();
        boolean enforce = isEnforceMode();
        dispatcher.addFilter(new ModeAwareFilter(
                "auth",
                new DrirpAuthFilter(authProvider, security.isAllowAnonymous()),
                enforce,
                drirpAuditService,
                security.isAuditEnabled()
        ));
        dispatcher.addFilter(new ModeAwareFilter(
                "replay",
                new DrirpReplayProtectionFilter(
                        security.getMaxSkewMs(),
                        security.getNonceTtlMs(),
                        security.getReplayMaxEntries(),
                        security.isAllowAnonymous()
                ),
                enforce,
                drirpAuditService,
                security.isAuditEnabled()
        ));
        dispatcher.setResponseSigner(authProvider);
        log.info("DRIRP security applied, mode={}, requireResponseAuth={}", securityMode(), security.isRequireResponseAuth());
    }

    public boolean isEnabled() {
        BackendV2Properties.DrirpSecurityProperties security = resolveSecurity();
        return security.isEnabled() && !isBlank(security.getSharedSecret());
    }

    public boolean isEnforceMode() {
        return "enforce".equalsIgnoreCase(securityMode());
    }

    public String securityMode() {
        return trim(resolveSecurity().getMode(), "enforce");
    }

    private DrirpAuthProvider buildAuthProvider() {
        BackendV2Properties.DrirpSecurityProperties security = resolveSecurity();
        if (!security.isEnabled()) {
            return null;
        }
        String sharedSecret = trim(security.getSharedSecret(), "");
        if (isBlank(sharedSecret)) {
            log.warn("dris.v2.drirp-security.enabled=true but shared-secret is empty, security will not be applied");
            return null;
        }
        String keyId = trim(security.getKeyId(), properties == null ? "" : properties.getNodeId());
        return new HmacSha256AuthProvider(sharedSecret, keyId, Math.max(0L, security.getMaxSkewMs()));
    }

    private BackendV2Properties.DrirpSecurityProperties resolveSecurity() {
        if (properties == null || properties.getDrirpSecurity() == null) {
            return new BackendV2Properties.DrirpSecurityProperties();
        }
        return properties.getDrirpSecurity();
    }

    private static String trim(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ModeAwareFilter implements DrirpRequestFilter {
        private final String name;
        private final DrirpRequestFilter delegate;
        private final boolean enforce;
        private final DrirpAuditService drirpAuditService;
        private final boolean auditEnabled;

        private ModeAwareFilter(
                String name,
                DrirpRequestFilter delegate,
                boolean enforce,
                DrirpAuditService drirpAuditService,
                boolean auditEnabled
        ) {
            this.name = name == null ? "filter" : name;
            this.delegate = delegate;
            this.enforce = enforce;
            this.drirpAuditService = drirpAuditService;
            this.auditEnabled = auditEnabled;
        }

        @Override
        public DrirpMessage beforeHandle(DrirpMessage request) {
            if (delegate == null) {
                return null;
            }
            DrirpMessage early = delegate.beforeHandle(request);
            if (early == null) {
                return null;
            }
            String traceId = request == null ? null : request.getTraceId();
            String message = trim(
                    early.getErrorMessage() == null ? early.getMsg() : early.getErrorMessage(),
                    "security check failed"
            );
            if (auditEnabled && drirpAuditService != null) {
                String path = "INBOUND_SECURITY/" + name + "/opcode=" + (request == null ? 0 : request.getCode());
                drirpAuditService.record("SECURITY", traceId, "", path, 401, 0L, false, message);
            }
            if (enforce) {
                return early;
            }
            log.warn("drirp {} filter rejected request in permissive mode, traceId={}, reason={}", name, traceId, message);
            return null;
        }

        private static String trim(String value, String fallback) {
            if (value == null || value.trim().isEmpty()) {
                return fallback;
            }
            return value.trim();
        }
    }
}
