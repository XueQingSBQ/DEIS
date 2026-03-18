package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchMode;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.SearchDocumentRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchScoreCalculatorTest {
    private final SearchScoreCalculator calculator = new SearchScoreCalculator(new SearchDocumentTextBuilder());
    private final DefaultSearchQueryAnalyzer analyzer = new DefaultSearchQueryAnalyzer(new DefaultSearchSynonymRegistry());

    @Test
    void computeLocalScore_shouldRewardExactNameMatch() {
        SearchRequest request = new SearchRequest();
        request.setMode(SearchMode.HYBRID);
        request.setNaturalQuery("electric vehicle population");
        SearchQueryAnalysis analysis = analyzer.analyze(request);

        SearchDocumentRow strong = row(
                "id-strong",
                "Electric Vehicle Population Data",
                "Battery electric vehicle registry dataset"
        );
        SearchDocumentRow weak = row(
                "id-weak",
                "Transportation catalog",
                "This resource includes electric vehicle population trends across counties"
        );

        SearchResult strongResult = new SearchResult();
        strongResult.setIdentifier("id-strong");
        strongResult.setKeywordScore(6.0);
        strongResult.setSemanticScore(0.35);

        SearchResult weakResult = new SearchResult();
        weakResult.setIdentifier("id-weak");
        weakResult.setKeywordScore(6.0);
        weakResult.setSemanticScore(0.35);

        double strongScore = calculator.computeLocalScore(request, analysis, strongResult, strong);
        double weakScore = calculator.computeLocalScore(request, analysis, weakResult, weak);

        assertTrue(strongScore > weakScore);
    }

    private SearchDocumentRow row(String identifier, String name, String description) {
        SearchDocumentRow row = new SearchDocumentRow();
        row.setIdentifier(identifier);
        row.setName(name);
        row.setDescription(description);
        row.setApplicationScenario("research");
        row.setFormat("csv");
        row.setAccessMethod("download");
        row.setLocator("https://example.com/" + identifier + ".csv");
        return row;
    }
}
