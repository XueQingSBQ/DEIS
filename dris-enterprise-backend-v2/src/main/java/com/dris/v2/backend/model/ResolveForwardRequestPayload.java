package com.dris.v2.backend.model;

import java.util.ArrayList;
import java.util.List;

public class ResolveForwardRequestPayload {
    private String identifier;
    private String resolveType;
    private Integer ttl;
    private List<String> visitedUrls = new ArrayList<String>();

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getResolveType() {
        return resolveType;
    }

    public void setResolveType(String resolveType) {
        this.resolveType = resolveType;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public List<String> getVisitedUrls() {
        return visitedUrls;
    }

    public void setVisitedUrls(List<String> visitedUrls) {
        this.visitedUrls = visitedUrls == null ? new ArrayList<String>() : visitedUrls;
    }
}
