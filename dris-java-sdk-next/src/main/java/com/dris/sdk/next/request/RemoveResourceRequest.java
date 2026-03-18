package com.dris.sdk.next.request;

public class RemoveResourceRequest {
    private String identifier;
    private Long expectedRevision;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public Long getExpectedRevision() {
        return expectedRevision;
    }

    public void setExpectedRevision(Long expectedRevision) {
        this.expectedRevision = expectedRevision;
    }
}
