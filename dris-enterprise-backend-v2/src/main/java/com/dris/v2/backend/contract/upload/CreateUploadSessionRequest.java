package com.dris.v2.backend.contract.upload;

public class CreateUploadSessionRequest {
    private String profile;
    private String filename;
    private long size;
    private String sha256;
    private String serviceEndpoint;

    public CreateUploadSessionRequest() {
    }

    public void validate() {
        if (profile == null || profile.trim().isEmpty()) {
            throw new IllegalArgumentException("profile is required");
        }
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getServiceEndpoint() {
        return serviceEndpoint;
    }

    public void setServiceEndpoint(String serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint;
    }
}
