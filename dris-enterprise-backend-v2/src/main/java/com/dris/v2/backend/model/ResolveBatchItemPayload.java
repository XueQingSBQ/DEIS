package com.dris.v2.backend.model;

import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;

public class ResolveBatchItemPayload {
    private String identifier;
    private Boolean success = Boolean.FALSE;
    private String code;
    private String message;
    private IdentifierMetadata metadata;
    private DigitalObject digitalObject;
    private Resource resource;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
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

    public DigitalObject getDigitalObject() {
        return digitalObject;
    }

    public void setDigitalObject(DigitalObject digitalObject) {
        this.digitalObject = digitalObject;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }
}

