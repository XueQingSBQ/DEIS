package com.dris.v2.backend.dto;

import com.dris.v2.backend.contract.search.SearchResult;

import java.util.ArrayList;
import java.util.List;

public class GlobalSearchResponse {
    private List<SearchResult> items = new ArrayList<SearchResult>();
    private boolean partial;
    private String anchorNodeCode;
    private List<String> visitedNodeCodes = new ArrayList<String>();
    private List<String> failedNodes = new ArrayList<String>();
    private String traceId;

    public List<SearchResult> getItems() {
        return items;
    }

    public void setItems(List<SearchResult> items) {
        this.items = items == null ? new ArrayList<SearchResult>() : items;
    }

    public boolean isPartial() {
        return partial;
    }

    public void setPartial(boolean partial) {
        this.partial = partial;
    }

    public String getAnchorNodeCode() {
        return anchorNodeCode;
    }

    public void setAnchorNodeCode(String anchorNodeCode) {
        this.anchorNodeCode = anchorNodeCode;
    }

    public List<String> getVisitedNodeCodes() {
        return visitedNodeCodes;
    }

    public void setVisitedNodeCodes(List<String> visitedNodeCodes) {
        this.visitedNodeCodes = visitedNodeCodes == null ? new ArrayList<String>() : visitedNodeCodes;
    }

    public List<String> getFailedNodes() {
        return failedNodes;
    }

    public void setFailedNodes(List<String> failedNodes) {
        this.failedNodes = failedNodes == null ? new ArrayList<String>() : failedNodes;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
