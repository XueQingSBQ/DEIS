package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultSearchQueryAnalyzerTest {

    @Test
    void analyze_shouldInferStructuredFiltersFromQuery() {
        DefaultSearchQueryAnalyzer analyzer = new DefaultSearchQueryAnalyzer(new DefaultSearchSynonymRegistry());
        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("检索湖南省通知公告，关注水利部山洪灾害防治");

        SearchQueryAnalysis analysis = analyzer.analyze(request);

        assertEquals("通知公告", analysis.getEffectiveFilters().get("theme"));
        assertEquals("湖南省", analysis.getEffectiveFilters().get("region"));
        assertEquals("水利部", analysis.getEffectiveFilters().get("nodeTarget"));
    }

    @Test
    void analyze_shouldAvoidOverFilteringWhenMultipleThemesExist() {
        DefaultSearchQueryAnalyzer analyzer = new DefaultSearchQueryAnalyzer(new DefaultSearchSynonymRegistry());
        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("检索山洪灾害分析评价相关规范文件和技术要求");

        SearchQueryAnalysis analysis = analyzer.analyze(request);

        assertFalse(analysis.getEffectiveFilters().containsKey("theme"));
    }
}
