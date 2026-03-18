package com.dris.sdk.next.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class Resource {
    private String componentType;
    private String storageType;
    private String accessMethod;
    private String contentType;
    private String format;
    private String locator;
    private long size;
    private String hashValue;
    private String etag;
    private String versionId;
    private String createdAt;
    private Map<String, Object> extra = new LinkedHashMap<String, Object>();

    public void validate() {
        if (componentType == null || componentType.trim().isEmpty()) {
            throw new IllegalArgumentException("componentType is required");
        }
        if (storageType == null || storageType.trim().isEmpty()) {
            throw new IllegalArgumentException("storageType is required");
        }
        if (accessMethod == null || accessMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("accessMethod is required");
        }
        if (locator == null || locator.trim().isEmpty()) {
            throw new IllegalArgumentException("locator is required");
        }
    }

    public Resource copy() {
        Resource copy = new Resource();
        copy.componentType = componentType;
        copy.storageType = storageType;
        copy.accessMethod = accessMethod;
        copy.contentType = contentType;
        copy.format = format;
        copy.locator = locator;
        copy.size = size;
        copy.hashValue = hashValue;
        copy.etag = etag;
        copy.versionId = versionId;
        copy.createdAt = createdAt;
        copy.extra = new LinkedHashMap<String, Object>(extra);
        return copy;
    }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public String getAccessMethod() { return accessMethod; }
    public void setAccessMethod(String accessMethod) { this.accessMethod = accessMethod; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getLocator() { return locator; }
    public void setLocator(String locator) { this.locator = locator; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getHashValue() { return hashValue; }
    public void setHashValue(String hashValue) { this.hashValue = hashValue; }
    public String getEtag() { return etag; }
    public void setEtag(String etag) { this.etag = etag; }
    public String getVersionId() { return versionId; }
    public void setVersionId(String versionId) { this.versionId = versionId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) {
        this.extra = extra == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(extra);
    }
}
