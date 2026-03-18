package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.SearchDocumentRow;
import com.dris.v2.backend.service.search.SearchResultSupport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class LocalSearchReranker {
    private final SearchScoreCalculator scoreCalculator;

    public LocalSearchReranker(SearchScoreCalculator scoreCalculator) {
        this.scoreCalculator = scoreCalculator;
    }

    public List<SearchResult> rerank(
            SearchRequest request,
            SearchQueryAnalysis analysis,
            Map<String, SearchResult> merged,
            Map<String, SearchDocumentRow> documents
    ) {
        List<SearchResult> out = new ArrayList<SearchResult>();
        if (merged == null || merged.isEmpty()) {
            return out;
        }
        for (SearchResult result : merged.values()) {
            if (result == null || SearchTextSupport.isBlank(result.getIdentifier())) {
                continue;
            }
            SearchDocumentRow row = documents.get(SearchTextSupport.trim(result.getIdentifier()));
            double localScore = scoreCalculator.computeLocalScore(request, analysis, result, row);
            result.setLocalScore(localScore);
            result.setFusionScore(0.0);
            result.setFinalScore(localScore);
            out.add(result);
        }
        out.sort(new Comparator<SearchResult>() {
            @Override
            public int compare(SearchResult left, SearchResult right) {
                return SearchResultSupport.compareByScoreDesc(left, right);
            }
        });
        SearchResultSupport.assignDisplayRanks(out, 1);
        return out;
    }
}
