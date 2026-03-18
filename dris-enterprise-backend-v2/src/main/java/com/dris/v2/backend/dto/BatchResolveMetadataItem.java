package com.dris.v2.backend.dto;

import com.dris.sdk.next.domain.IdentifierMetadata;

public class BatchResolveMetadataItem {
    private String identifier;
    private String targetNodeCode;
    private String resolvedByNodeCode;
    private boolean success;
    private String code;
    private String message;
    private IdentifierMetadata metadata;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getTargetNodeCode() {
        return targetNodeCode;
    }

    public void setTargetNodeCode(String targetNodeCode) {
        this.targetNodeCode = targetNodeCode;
    }

    public String getResolvedByNodeCode() {
        return resolvedByNodeCode;
    }

    public void setResolvedByNodeCode(String resolvedByNodeCode) {
        this.resolvedByNodeCode = resolvedByNodeCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public IdentifierMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(IdentifierMetadata metadata) {
        this.metadata = metadata;
    }
}

