package com.dris.v2.backend.model;

import com.dris.v2.backend.contract.search.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class SearchForwardResponsePayload {
    private Boolean delegate = Boolean.FALSE;
    private Boolean anchorFound = Boolean.FALSE;
    private String anchorNodeCode;
    private String anchorDrirpUrl;
    private String nextHopCode;
    private String nextHopDrirpUrl;
    private List<SearchForwardHop> nextHopList = new ArrayList<SearchForwardHop>();
    private List<SearchResult> hits = new ArrayList<SearchResult>();
    private Boolean partial = Boolean.FALSE;
    private List<SearchForwardError> errors = new ArrayList<SearchForwardError>();
    private Integer ttlRemaining;
    private List<String> visitedNodeCodes = new ArrayList<String>();
    private List<String> visitedUrls = new ArrayList<String>();
    private String traceId;

    public Boolean getDelegate() {
        return delegate;
    }

    public void setDelegate(Boolean delegate) {
        this.delegate = delegate;
    }

    public Boolean getAnchorFound() {
        return anchorFound;
    }

    public void setAnchorFound(Boolean anchorFound) {
        this.anchorFound = anchorFound;
    }

    public String getAnchorNodeCode() {
        return anchorNodeCode;
    }

    public void setAnchorNodeCode(String anchorNodeCode) {
        this.anchorNodeCode = anchorNodeCode;
    }

    public String getAnchorDrirpUrl() {
        return anchorDrirpUrl;
    }

    public void setAnchorDrirpUrl(String anchorDrirpUrl) {
        this.anchorDrirpUrl = anchorDrirpUrl;
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

    public List<SearchForwardHop> getNextHopList() {
        return nextHopList;
    }

    public void setNextHopList(List<SearchForwardHop> nextHopList) {
        this.nextHopList = nextHopList == null ? new ArrayList<SearchForwardHop>() : nextHopList;
    }

    public List<SearchResult> getHits() {
        return hits;
    }

    public void setHits(List<SearchResult> hits) {
        this.hits = hits == null ? new ArrayList<SearchResult>() : hits;
    }

    public Boolean getPartial() {
        return partial;
    }

    public void setPartial(Boolean partial) {
        this.partial = partial;
    }

    public List<SearchForwardError> getErrors() {
        return errors;
    }

    public void setErrors(List<SearchForwardError> errors) {
        this.errors = errors == null ? new ArrayList<SearchForwardError>() : errors;
    }

    public Integer getTtlRemaining() {
        return ttlRemaining;
    }

    public void setTtlRemaining(Integer ttlRemaining) {
        this.ttlRemaining = ttlRemaining;
    }

    public List<String> getVisitedNodeCodes() {
        return visitedNodeCodes;
    }

    public void setVisitedNodeCodes(List<String> visitedNodeCodes) {
        this.visitedNodeCodes = visitedNodeCodes == null ? new ArrayList<String>() : visitedNodeCodes;
    }

    public List<String> getVisitedUrls() {
        return visitedUrls;
    }

    public void setVisitedUrls(List<String> visitedUrls) {
        this.visitedUrls = visitedUrls == null ? new ArrayList<String>() : visitedUrls;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
