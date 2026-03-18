package com.dris.auth.center.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dris.auth")
public class AuthProperties {
    private String issuer = "auth-center";
    private String audience = "dris-business-node";
    private long accessTokenTtlSeconds = 7200L;
    private String keyId = "dris-v2-auth-rs256";
    private String keySeed = "dris-v2-auth-center-key-seed";

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

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeySeed() {
        return keySeed;
    }

    public void setKeySeed(String keySeed) {
        this.keySeed = keySeed;
    }
}
