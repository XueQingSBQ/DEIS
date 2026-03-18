package com.dris.v2.backend.contract.registration;

import com.dris.sdk.next.domain.IdentifierMetadata;

public class IdentifierRegistrationView {
    private String identifier;
    private String status;
    private boolean metadataCreated;
    private boolean resourceBound;
    private String createTime;
    private String updateTime;

    public static IdentifierRegistrationView from(String identifier, IdentifierMetadata metadata) {
        IdentifierRegistrationView view = new IdentifierRegistrationView();
        view.setIdentifier(identifier == null ? "" : identifier.trim());
        view.setMetadataCreated(metadata != null);
        view.setResourceBound(false);
        if (metadata != null) {
            view.setStatus(metadata.getStatus());
            view.setCreateTime(metadata.getCreateTime());
            view.setUpdateTime(metadata.getUpdateTime());
        }
        return view;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isMetadataCreated() {
        return metadataCreated;
    }

    public void setMetadataCreated(boolean metadataCreated) {
        this.metadataCreated = metadataCreated;
    }

    public boolean isResourceBound() {
        return resourceBound;
    }

    public void setResourceBound(boolean resourceBound) {
        this.resourceBound = resourceBound;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
