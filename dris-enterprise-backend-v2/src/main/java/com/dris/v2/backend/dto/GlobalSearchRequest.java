package com.dris.v2.backend.dto;

import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GlobalSearchRequest {
    private String naturalQuery;
    private List<String> keywords = new ArrayList<String>();
    private List<String> fields = new ArrayList<String>();
    private Map<String, String> filters = new LinkedHashMap<String, String>();
    private int limit = 20;
    private int offset = 0;
    private SearchMode mode = SearchMode.HYBRID;
    private double semanticWeight = 0.7;
    private String strategy = "RECURSIVE";
    private String anchorNodeCode;
    private Integer ttl;
    private Integer maxFanout;
    private Integer perNodeLimit;
    private Long globalDeadlineMs;
    private Long hopTimeoutMs;
    private String traceId;

    public SearchRequest toSearchRequest() {
        SearchRequest request = new SearchRequest();
        request.setNaturalQuery(naturalQuery);
        request.setKeywords(keywords);
        request.setFields(fields);
        request.setFilters(filters);
        request.setLimit(limit);
        request.setOffset(offset);
        request.setMode(mode);
        request.setSemanticWeight(semanticWeight);
        return request;
    }

    public String getNaturalQuery() {
        return naturalQuery;
    }

    public void setNaturalQuery(String naturalQuery) {
        this.naturalQuery = naturalQuery;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords == null ? new ArrayList<String>() : keywords;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<String>() : fields;
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, String> filters) {
        this.filters = filters == null ? new LinkedHashMap<String, String>() : filters;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public SearchMode getMode() {
        return mode;
    }

    public void setMode(SearchMode mode) {
        this.mode = mode;
    }

    public double getSemanticWeight() {
        return semanticWeight;
    }

    public void setSemanticWeight(double semanticWeight) {
        this.semanticWeight = semanticWeight;
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

    public Long getGlobalDeadlineMs() {
        return globalDeadlineMs;
    }

    public void setGlobalDeadlineMs(Long globalDeadlineMs) {
        this.globalDeadlineMs = globalDeadlineMs;
    }

    public Long getHopTimeoutMs() {
        return hopTimeoutMs;
    }

    public void setHopTimeoutMs(Long hopTimeoutMs) {
        this.hopTimeoutMs = hopTimeoutMs;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
