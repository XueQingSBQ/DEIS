package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchMode;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.IdentifierSearchDao;
import com.dris.v2.backend.dao.SearchDocumentRow;
import com.dris.v2.backend.vector.VectorIndexGateway;
import com.dris.v2.backend.vector.VectorSearchHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LocalSearchCandidateAssembler {
    private final KeywordSearchGateway keywordSearchGateway;
    private final IdentifierSearchDao identifierSearchDao;
    private final VectorIndexGateway vectorIndexGateway;
    private final SearchScoreCalculator scoreCalculator;
    private final SearchResultFactory searchResultFactory;
    private final SearchDocumentFilter searchDocumentFilter;

    public LocalSearchCandidateAssembler(
            KeywordSearchGateway keywordSearchGateway,
            IdentifierSearchDao identifierSearchDao,
            VectorIndexGateway vectorIndexGateway,
            SearchScoreCalculator scoreCalculator,
            SearchResultFactory searchResultFactory,
            SearchDocumentFilter searchDocumentFilter
    ) {
        this.keywordSearchGateway = keywordSearchGateway;
        this.identifierSearchDao = identifierSearchDao;
        this.vectorIndexGateway = vectorIndexGateway;
        this.scoreCalculator = scoreCalculator;
        this.searchResultFactory = searchResultFactory;
        this.searchDocumentFilter = searchDocumentFilter;
    }

    public SearchMode resolveMode(SearchRequest request) {
        return request == null || request.getMode() == null ? SearchMode.HYBRID : request.getMode();
    }

    public int resolveCandidateLimit(SearchRequest request) {
        if (request == null) {
            return 0;
        }
        int requested = request.getLimit() + request.getOffset();
        return Math.min(Math.max(requested * 4, requested), 5000);
    }

    public LocalSearchWorkingSet collect(SearchRequest request, SearchQueryAnalysis analysis, SearchMode mode, int candidateLimit) {
        LocalSearchWorkingSet workingSet = new LocalSearchWorkingSet();
        if (mode == SearchMode.KEYWORD || mode == SearchMode.HYBRID) {
            collectKeywordCandidates(workingSet, request, analysis, candidateLimit);
        }
        if (mode == SearchMode.SEMANTIC || mode == SearchMode.HYBRID) {
            collectSemanticCandidates(workingSet, request, analysis, candidateLimit);
        }
        return workingSet;
    }

    public LocalSearchWorkingSet collectFallback(SearchRequest request, SearchQueryAnalysis analysis, SearchMode mode) {
        LocalSearchWorkingSet workingSet = new LocalSearchWorkingSet();
        List<SearchDocumentRow> snapshot = identifierSearchDao.findAllActive();
        for (SearchDocumentRow row : snapshot) {
            if (!searchDocumentFilter.matches(row, analysis.getEffectiveFilters())) {
                continue;
            }
            SearchResult candidate = buildFallbackCandidate(row, request, analysis, mode);
            if (candidate == null) {
                continue;
            }
            workingSet.putDocument(row);
            workingSet.addKeywordCandidate(candidate);
        }
        return workingSet;
    }

    private void collectKeywordCandidates(
            LocalSearchWorkingSet workingSet,
            SearchRequest request,
            SearchQueryAnalysis analysis,
            int candidateLimit
    ) {
        List<SearchDocumentRow> rows = keywordSearchGateway.search(
                analysis.getRetrievalTokens(),
                request.getFields(),
                analysis.getEffectiveFilters(),
                candidateLimit,
                0
        );
        for (SearchDocumentRow row : rows) {
            if (row == null || SearchTextSupport.isBlank(row.getIdentifier())) {
                continue;
            }
            workingSet.putDocument(row);
            SearchResult candidate = buildKeywordCandidate(row, request, analysis);
            if (candidate != null) {
                workingSet.addKeywordCandidate(candidate);
            }
        }
    }

    private void collectSemanticCandidates(
            LocalSearchWorkingSet workingSet,
            SearchRequest request,
            SearchQueryAnalysis analysis,
            int candidateLimit
    ) {
        String semanticQuery = SearchTextSupport.isBlank(analysis.getExpandedNaturalQuery())
                ? request.getNaturalQuery()
                : analysis.getExpandedNaturalQuery();
        List<VectorSearchHit> hits = vectorIndexGateway.search(semanticQuery, candidateLimit);
        if (hits == null || hits.isEmpty()) {
            return;
        }
        List<String> identifiers = new ArrayList<String>();
        for (VectorSearchHit hit : hits) {
            if (hit != null && !SearchTextSupport.isBlank(hit.getIdentifier())) {
                identifiers.add(SearchTextSupport.trim(hit.getIdentifier()));
            }
        }
        Map<String, SearchDocumentRow> rows = identifierSearchDao.findActiveByIdentifiers(identifiers);
        for (VectorSearchHit hit : hits) {
            if (hit == null || SearchTextSupport.isBlank(hit.getIdentifier())) {
                continue;
            }
            String identifier = SearchTextSupport.trim(hit.getIdentifier());
            SearchDocumentRow row = rows.get(identifier);
            if (!searchDocumentFilter.matches(row, analysis.getEffectiveFilters())) {
                continue;
            }
            workingSet.putDocument(row);
            SearchResult result = workingSet.getOrCreate(row, searchResultFactory);
            if (result != null) {
                result.setSemanticScore(Math.max(result.getSemanticScore(), hit.getScore()));
            }
        }
    }

    private SearchResult buildKeywordCandidate(SearchDocumentRow row, SearchRequest request, SearchQueryAnalysis analysis) {
        SearchResult result = searchResultFactory.fromRow(row);
        double keywordScore = row == null ? 0.0 : row.getScore();
        if (keywordScore <= 0.0) {
            keywordScore = scoreCalculator.computeKeywordScore(analysis, request.getFields(), row);
        }
        if (keywordScore <= 0.0) {
            return null;
        }
        result.setKeywordScore(keywordScore);
        return result;
    }

    private SearchResult buildFallbackCandidate(
            SearchDocumentRow row,
            SearchRequest request,
            SearchQueryAnalysis analysis,
            SearchMode mode
    ) {
        SearchResult result = searchResultFactory.fromRow(row);
        double keywordScore = scoreCalculator.computeKeywordScore(analysis, request.getFields(), row);
        double semanticScore = scoreCalculator.computeSemanticOverlap(analysis, row);

        if (mode == SearchMode.KEYWORD && keywordScore <= 0.0) {
            return null;
        }
        if (mode == SearchMode.SEMANTIC && semanticScore <= 0.0) {
            return null;
        }
        if (mode == SearchMode.HYBRID && keywordScore <= 0.0 && semanticScore <= 0.0) {
            return null;
        }
        result.setKeywordScore(keywordScore);
        result.setSemanticScore(semanticScore);
        return result;
    }
}
