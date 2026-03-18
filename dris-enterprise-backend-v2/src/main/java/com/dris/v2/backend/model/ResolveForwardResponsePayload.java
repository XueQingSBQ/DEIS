package com.dris.v2.backend.model;

import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;

import java.util.ArrayList;
import java.util.List;

public class ResolveForwardResponsePayload {
    private Boolean delegate = Boolean.FALSE;
    private String resolveType;
    private String nextHopCode;
    private String nextHopDrirpUrl;
    private Integer ttlRemaining;
    private List<String> visitedUrls = new ArrayList<String>();
    private String resolvedBy;
    private String mode;
    private IdentifierMetadata metadata;
    private DigitalObject digitalObject;
    private Resource resource;

    public Boolean getDelegate() {
        return delegate;
    }

    public void setDelegate(Boolean delegate) {
        this.delegate = delegate;
    }

    public String getResolveType() {
        return resolveType;
    }

    public void setResolveType(String resolveType) {
        this.resolveType = resolveType;
    }

    public String getNextHopCode() {
        return nextHopCode;
    }

    public void setNextHopCode(String nextHopCode) {
        this.nextHopCode = nextHopCode;
    }

    public String getNextHopDrirpUrl() {
        return nextHopDrirpUrl;
    }

    public void setNextHopDrirpUrl(String nextHopDrirpUrl) {
        this.nextHopDrirpUrl = nextHopDrirpUrl;
    }

    public Integer getTtlRemaining() {
        return ttlRemaining;
    }

    public void setTtlRemaining(Integer ttlRemaining) {
        this.ttlRemaining = ttlRemaining;
    }

    public List<String> getVisitedUrls() {
        return visitedUrls;
    }

    public void setVisitedUrls(List<String> visitedUrls) {
        this.visitedUrls = visitedUrls == null ? new ArrayList<String>() : visitedUrls;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
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

