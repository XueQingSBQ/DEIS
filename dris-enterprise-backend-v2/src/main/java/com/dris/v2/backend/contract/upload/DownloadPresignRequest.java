package com.dris.v2.backend.contract.upload;

public class DownloadPresignRequest {
    private String identifier;
    private int expiresInSeconds = 600;

    public DownloadPresignRequest() {
    }

    public DownloadPresignRequest(String identifier, int expiresInSeconds) {
        this.identifier = identifier;
        this.expiresInSeconds = expiresInSeconds;
    }

    public void validate() {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("identifier is required");
        }
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public int getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(int expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
