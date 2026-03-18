package com.dris.sdk.next.domain;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResourceVersion {
    private String identifier;
    private long versionNo;
    private String componentType;
    private String storageType;
    private String accessMethod;
    private String contentType;
    private String format;
    private String locator;
    private long size;
    private String hashValue;
    private String etag;
    private String providerVersionId;
    private String sourceUploadId;
    private String bindMode;
    private String verifyStatus;
    private String createdAt;
    private Map<String, Object> extra = new LinkedHashMap<String, Object>();

    public ResourceVersion copy() {
        ResourceVersion copy = new ResourceVersion();
        copy.identifier = identifier;
        copy.versionNo = versionNo;
        copy.componentType = componentType;
        copy.storageType = storageType;
        copy.accessMethod = accessMethod;
        copy.contentType = contentType;
        copy.format = format;
        copy.locator = locator;
        copy.size = size;
        copy.hashValue = hashValue;
        copy.etag = etag;
        copy.providerVersionId = providerVersionId;
        copy.sourceUploadId = sourceUploadId;
        copy.bindMode = bindMode;
        copy.verifyStatus = verifyStatus;
        copy.createdAt = createdAt;
        copy.extra = new LinkedHashMap<String, Object>(extra);
        return copy;
    }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public long getVersionNo() { return versionNo; }
    public void setVersionNo(long versionNo) { this.versionNo = versionNo; }
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
    public String getProviderVersionId() { return providerVersionId; }
    public void setProviderVersionId(String providerVersionId) { this.providerVersionId = providerVersionId; }
    public String getSourceUploadId() { return sourceUploadId; }
    public void setSourceUploadId(String sourceUploadId) { this.sourceUploadId = sourceUploadId; }
    public String getBindMode() { return bindMode; }
    public void setBindMode(String bindMode) { this.bindMode = bindMode; }
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) {
        this.extra = extra == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(extra);
    }
}
