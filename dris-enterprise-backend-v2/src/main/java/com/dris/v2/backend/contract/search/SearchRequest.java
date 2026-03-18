package com.dris.v2.backend.contract.search;



import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchRequest {
    private String naturalQuery;
    private List<String> keywords = new ArrayList<>();
    private List<String> fields = new ArrayList<>();
    private Map<String, String> filters = new LinkedHashMap<>();
    private int limit = 20;
    private int offset = 0;
    private SearchMode mode = SearchMode.HYBRID;
    private double semanticWeight = 0.7;

    public SearchRequest() {
    }

    public void validate() {
        if (naturalQuery == null || naturalQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("naturalQuery is required");
        }
        if (limit <= 0 || limit > 200) {
            throw new IllegalArgumentException("limit must be in range 1..200");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (semanticWeight < 0 || semanticWeight > 1) {
            throw new IllegalArgumentException("semanticWeight must be in range 0..1");
        }
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
        this.keywords = keywords == null ? new ArrayList<>() : keywords;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields == null ? new ArrayList<>() : fields;
    }

    public Map<String, String> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, String> filters) {
        this.filters = filters == null ? new LinkedHashMap<>() : filters;
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
}
