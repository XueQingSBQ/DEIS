package com.dris.v2.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dris.v2.auth")
public class AuthProperties {
    private boolean apiAuthRequired = false;
    private boolean writePermissionEnabled = false;
    private String issuer = "auth-center";
    private String audience = "dris-business-node";
    private String authCenterBaseUrl = "http://127.0.0.1:8290";
    private String publicKeyPem = "";
    private long publicKeyRefreshMs = 300000L;

    public boolean isApiAuthRequired() {
        return apiAuthRequired;
    }

    public void setApiAuthRequired(boolean apiAuthRequired) {
        this.apiAuthRequired = apiAuthRequired;
    }

    public boolean isWritePermissionEnabled() {
        return writePermissionEnabled;
    }

    public void setWritePermissionEnabled(boolean writePermissionEnabled) {
        this.writePermissionEnabled = writePermissionEnabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getAuthCenterBaseUrl() {
        return authCenterBaseUrl;
    }

    public void setAuthCenterBaseUrl(String authCenterBaseUrl) {
        this.authCenterBaseUrl = authCenterBaseUrl;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public long getPublicKeyRefreshMs() {
        return publicKeyRefreshMs;
    }

    public void setPublicKeyRefreshMs(long publicKeyRefreshMs) {
        this.publicKeyRefreshMs = publicKeyRefreshMs;
    }
}
