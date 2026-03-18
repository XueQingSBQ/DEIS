package com.dris.sdk.next.domain;

import com.dris.sdk.next.enumtype.ObjectStatus;

public class DigitalObject {
    private String identifier;
    private IdentifierMetadata metadata;
    private Resource resource;
    private ObjectStatus status = ObjectStatus.UNKNOWN;
    private long revision;

    public void validateInvariants() {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("identifier is required");
        }
        if (metadata != null && metadata.getIdentifier() != null && !metadata.getIdentifier().equals(identifier)) {
            throw new IllegalArgumentException("metadata.identifier must equal aggregate identifier");
        }
    }

    public DigitalObject copy() {
        DigitalObject copy = new DigitalObject();
        copy.identifier = identifier;
        copy.metadata = metadata == null ? null : metadata.copy();
        copy.resource = resource == null ? null : resource.copy();
        copy.status = status;
        copy.revision = revision;
        return copy;
    }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public IdentifierMetadata getMetadata() { return metadata; }
    public void setMetadata(IdentifierMetadata metadata) { this.metadata = metadata; }
    public Resource getResource() { return resource; }
    public void setResource(Resource resource) { this.resource = resource; }
    public ObjectStatus getStatus() { return status; }
    public void setStatus(ObjectStatus status) { this.status = status; }
    public long getRevision() { return revision; }
    public void setRevision(long revision) { this.revision = revision; }
}