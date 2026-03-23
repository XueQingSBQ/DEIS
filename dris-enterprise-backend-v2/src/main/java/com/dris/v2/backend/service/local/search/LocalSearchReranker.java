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
    private static final double MIN_RETURN_SCORE = 0.18;
    private static final double TOP_SCORE_RATIO = 0.60;
    private static final double CLIFF_RATIO = 0.62;

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
        out = pruneTail(analysis, out, documents);
        SearchResultSupport.assignDisplayRanks(out, 1);
        return out;
    }

    private List<SearchResult> pruneTail(
            SearchQueryAnalysis analysis,
            List<SearchResult> sorted,
            Map<String, SearchDocumentRow> documents
    ) {
        List<SearchResult> kept = new ArrayList<SearchResult>();
        if (sorted == null || sorted.isEmpty()) {
            return kept;
        }
        double topScore = sorted.get(0).getFinalScore();
        double minAllowed = Math.max(MIN_RETURN_SCORE, topScore * TOP_SCORE_RATIO);
        double previousScore = -1.0;
        for (SearchResult result : sorted) {
            if (result == null) {
                continue;
            }
            if (result.getFinalScore() < minAllowed) {
                break;
            }
            if (previousScore > 0.0 && (result.getFinalScore() / previousScore) < CLIFF_RATIO) {
                break;
            }
            SearchDocumentRow row = documents.get(SearchTextSupport.trim(result.getIdentifier()));
            if (!scoreCalculator.hasPrimaryFieldMatch(analysis, row)) {
                continue;
            }
            kept.add(result);
            previousScore = result.getFinalScore();
        }
        if (kept.isEmpty() && topScore >= MIN_RETURN_SCORE) {
            SearchResult fallback = sorted.get(0);
            SearchDocumentRow row = documents.get(SearchTextSupport.trim(fallback.getIdentifier()));
            if (row != null) {
                kept.add(fallback);
            }
        }
        return kept;
    }
}
