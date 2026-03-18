package com.dris.sdk.next.request;

import java.util.LinkedHashMap;
import java.util.Map;

public class PatchMetadataRequest {
    private String identifier;
    private Long expectedRevision;
    private Map<String, Object> changes = new LinkedHashMap<String, Object>();

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

    public Map<String, Object> getChanges() {
        return changes;
    }

    public void setChanges(Map<String, Object> changes) {
        this.changes = changes == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(changes);
    }
}
