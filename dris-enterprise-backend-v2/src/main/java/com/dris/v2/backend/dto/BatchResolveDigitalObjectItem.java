package com.dris.v2.backend.dto;

import com.dris.sdk.next.domain.DigitalObject;

public class BatchResolveDigitalObjectItem {
    private String identifier;
    private String targetNodeCode;
    private String resolvedByNodeCode;
    private boolean success;
    private String code;
    private String message;
    private DigitalObject digitalObject;

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

    public DigitalObject getDigitalObject() {
        return digitalObject;
    }

    public void setDigitalObject(DigitalObject digitalObject) {
        this.digitalObject = digitalObject;
    }
}

