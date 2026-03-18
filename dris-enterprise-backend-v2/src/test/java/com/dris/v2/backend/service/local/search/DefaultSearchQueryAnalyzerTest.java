package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchRequest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSearchQueryAnalyzerTest {

    private final DefaultSearchQueryAnalyzer analyzer = new DefaultSearchQueryAnalyzer(new DefaultSearchSynonymRegistry());

    @Test
    void analyze_shouldExpandTermsWithoutLeakingUnrelatedFilters() {
        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("energy dataset");

        SearchQueryAnalysis analysis = analyzer.analyze(request);

        assertEquals("dataset", analysis.getEffectiveFilters().get("entityType"));
        assertFalse(analysis.getEffectiveFilters().containsKey("format"));
        assertFalse(analysis.getEffectiveFilters().containsKey("accessMethod"));
        assertFalse(analysis.getEffectiveFilters().containsKey("storageType"));
        assertTrue(analysis.getExpandedTerms().contains("data set"));
        assertTrue(analysis.getRetrievalTokens().contains("energy"));
    }

    @Test
    void analyze_shouldNotOverrideExplicitFilters() {
        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("csv dataset");
        Map<String, String> filters = new LinkedHashMap<String, String>();
        filters.put("format", "json");
        request.setFilters(filters);

        SearchQueryAnalysis analysis = analyzer.analyze(request);

        assertEquals("json", analysis.getEffectiveFilters().get("format"));
        assertEquals("dataset", analysis.getEffectiveFilters().get("entityType"));
    }
}
