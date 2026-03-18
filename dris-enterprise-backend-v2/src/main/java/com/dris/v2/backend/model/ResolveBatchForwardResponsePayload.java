package com.dris.v2.backend.model;

import java.util.ArrayList;
import java.util.List;

public class ResolveBatchForwardResponsePayload {
    private Boolean delegate = Boolean.FALSE;
    private String resolveType;
    private String nextHopCode;
    private String nextHopDrirpUrl;
    private Integer ttlRemaining;
    private List<String> visitedUrls = new ArrayList<String>();
    private String resolvedBy;
    private List<ResolveBatchItemPayload> items = new ArrayList<ResolveBatchItemPayload>();

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

    public List<ResolveBatchItemPayload> getItems() {
        return items;
    }

    public void setItems(List<ResolveBatchItemPayload> items) {
        this.items = items == null ? new ArrayList<ResolveBatchItemPayload>() : items;
    }
}
