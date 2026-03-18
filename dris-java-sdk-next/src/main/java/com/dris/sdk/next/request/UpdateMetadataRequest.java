package com.dris.sdk.next.request;

import com.dris.sdk.next.domain.IdentifierMetadata;

public class UpdateMetadataRequest {
    private String identifier;
    private Long expectedRevision;
    private IdentifierMetadata metadata;

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

    public IdentifierMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(IdentifierMetadata metadata) {
        this.metadata = metadata;
    }
}
