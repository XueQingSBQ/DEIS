package com.dris.v2.backend.service.local.upload;

import java.util.LinkedHashMap;
import java.util.Map;

public class UploadSessionRecord {
    private String uploadId;
    private String identifier;
    private String componentType;
    private String bucket;
    private String objectKey;
    private String uploadMode;
    private long expectedSize;
    private String expectedSha256;
    private String status;
    private String expiresAt;
    private Map<String, Object> extra = new LinkedHashMap<String, Object>();

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getComponentType() {
        return componentType;
    }

    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getUploadMode() {
        return uploadMode;
    }

    public void setUploadMode(String uploadMode) {
        this.uploadMode = uploadMode;
    }

    public long getExpectedSize() {
        return expectedSize;
    }

    public void setExpectedSize(long expectedSize) {
        this.expectedSize = expectedSize;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public void setExpectedSha256(String expectedSha256) {
        this.expectedSha256 = expectedSha256;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(extra);
    }
}