package com.dris.v2.backend.dto;

import com.dris.sdk.next.domain.Resource;

public class BatchResolveResourceItem {
    private String identifier;
    private String targetNodeCode;
    private String resolvedByNodeCode;
    private boolean success;
    private String code;
    private String message;
    private Resource resource;

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

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }
}

