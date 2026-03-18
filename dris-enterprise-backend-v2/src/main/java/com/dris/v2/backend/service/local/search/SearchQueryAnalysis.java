package com.dris.v2.backend.service.local.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchQueryAnalysis {
    private final String originalQuery;
    private final String expandedNaturalQuery;
    private final List<String> coreTokens;
    private final List<String> retrievalTokens;
    private final List<String> expandedTerms;
    private final Map<String, String> effectiveFilters;

    public SearchQueryAnalysis(
            String originalQuery,
            String expandedNaturalQuery,
            List<String> coreTokens,
            List<String> retrievalTokens,
            List<String> expandedTerms,
            Map<String, String> effectiveFilters
    ) {
        this.originalQuery = SearchTextSupport.trim(originalQuery);
        this.expandedNaturalQuery = SearchTextSupport.trim(expandedNaturalQuery);
        this.coreTokens = Collections.unmodifiableList(new ArrayList<String>(coreTokens == null ? Collections.<String>emptyList() : coreTokens));
        this.retrievalTokens = Collections.unmodifiableList(new ArrayList<String>(retrievalTokens == null ? Collections.<String>emptyList() : retrievalTokens));
        this.expandedTerms = Collections.unmodifiableList(new ArrayList<String>(expandedTerms == null ? Collections.<String>emptyList() : expandedTerms));
        this.effectiveFilters = Collections.unmodifiableMap(new LinkedHashMap<String, String>(effectiveFilters == null ? Collections.<String, String>emptyMap() : effectiveFilters));
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public String getExpandedNaturalQuery() {
        return expandedNaturalQuery;
    }

    public List<String> getCoreTokens() {
        return coreTokens;
    }

    public List<String> getRetrievalTokens() {
        return retrievalTokens;
    }

    public List<String> getExpandedTerms() {
        return expandedTerms;
    }

    public Map<String, String> getEffectiveFilters() {
        return effectiveFilters;
    }
}
