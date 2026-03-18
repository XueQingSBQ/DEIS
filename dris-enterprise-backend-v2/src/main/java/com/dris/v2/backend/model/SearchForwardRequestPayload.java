package com.dris.v2.backend.model;

import com.dris.v2.backend.contract.search.SearchRequest;

import java.util.ArrayList;
import java.util.List;

public class SearchForwardRequestPayload {
    private String phase;
    private String strategy;
    private String anchorNodeCode;
    private String entryNodeCode;
    private SearchRequest searchRequest;
    private Integer ttl;
    private Integer maxFanout;
    private Integer perNodeLimit;
    private List<String> visitedNodeCodes = new ArrayList<String>();
    private List<String> visitedUrls = new ArrayList<String>();
    private String traceId;

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getAnchorNodeCode() {
        return anchorNodeCode;
    }

    public void setAnchorNodeCode(String anchorNodeCode) {
        this.anchorNodeCode = anchorNodeCode;
    }

    public String getEntryNodeCode() {
        return entryNodeCode;
    }

    public void setEntryNodeCode(String entryNodeCode) {
        this.entryNodeCode = entryNodeCode;
    }

    public SearchRequest getSearchRequest() {
        return searchRequest;
    }

    public void setSearchRequest(SearchRequest searchRequest) {
        this.searchRequest = searchRequest;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public Integer getMaxFanout() {
        return maxFanout;
    }

    public void setMaxFanout(Integer maxFanout) {
        this.maxFanout = maxFanout;
    }

    public Integer getPerNodeLimit() {
        return perNodeLimit;
    }

    public void setPerNodeLimit(Integer perNodeLimit) {
        this.perNodeLimit = perNodeLimit;
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
