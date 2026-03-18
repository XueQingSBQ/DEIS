package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.search.SearchMode;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.IdentifierSearchDao;
import com.dris.v2.backend.dao.SearchDocumentRow;
import com.dris.v2.backend.metrics.BackendOperationMetrics;
import com.dris.v2.backend.service.search.SearchResultSupport;
import com.dris.v2.backend.vector.VectorIndexGateway;
import com.dris.v2.backend.vector.VectorSearchHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultLocalSearchService implements LocalSearchService {
    private final IdentifierSearchDao identifierSearchDao;
    private final VectorIndexGateway vectorIndexGateway;
    private final BackendOperationMetrics metrics;
    private final SearchQueryAnalyzer queryAnalyzer;
    private final SearchScoreCalculator scoreCalculator;
    private final LocalSearchReranker localSearchReranker;

    public DefaultLocalSearchService(
            IdentifierSearchDao identifierSearchDao,
            VectorIndexGateway vectorIndexGateway,
            BackendOperationMetrics metrics,
            SearchQueryAnalyzer queryAnalyzer,
            SearchScoreCalculator scoreCalculator,
            LocalSearchReranker localSearchReranker
    ) {
        this.identifierSearchDao = identifierSearchDao;
        this.vectorIndexGateway = vectorIndexGateway;
        this.metrics = metrics;
        this.queryAnalyzer = queryAnalyzer;
        this.scoreCalculator = scoreCalculator;
        this.localSearchReranker = localSearchReranker;
    }

    @Override
    public DrisResponse<List<SearchResult>> search(SearchRequest request) {
        long start = System.nanoTime();
        boolean ok = false;
        try {
            if (request == null) {
                return DrisResponse.failure("VALIDATION_ERROR", "request is required", null);
            }
            try {
                request.validate();
            } catch (Exception exception) {
                return DrisResponse.failure("VALIDATION_ERROR", safeMessage(exception), null);
            }

            SearchMode mode = request.getMode() == null ? SearchMode.HYBRID : request.getMode();
            SearchQueryAnalysis analysis = queryAnalyzer.analyze(request);
            int requested = request.getLimit() + request.getOffset();
            int candidateLimit = Math.min(Math.max(requested * 4, requested), 5000);

            Map<String, SearchResult> merged = new LinkedHashMap<String, SearchResult>();
            Map<String, SearchDocumentRow> documents = new LinkedHashMap<String, SearchDocumentRow>();

            if (mode == SearchMode.KEYWORD || mode == SearchMode.HYBRID) {
                List<SearchDocumentRow> rows = identifierSearchDao.searchFuzzy(
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
                    documents.put(SearchTextSupport.trim(row.getIdentifier()), row);
                    SearchResult candidate = buildKeywordCandidate(row, request, analysis);
                    if (candidate != null) {
                        mergeKeywordCandidate(merged, candidate);
                    }
                }
            }

            if (mode == SearchMode.SEMANTIC || mode == SearchMode.HYBRID) {
                String semanticQuery = SearchTextSupport.isBlank(analysis.getExpandedNaturalQuery())
                        ? request.getNaturalQuery()
                        : analysis.getExpandedNaturalQuery();
                List<VectorSearchHit> hits = vectorIndexGateway.search(semanticQuery, candidateLimit);
                if (hits != null && !hits.isEmpty()) {
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
                        if (row == null || !matchesFilters(row, analysis.getEffectiveFilters())) {
                            continue;
                        }
                        documents.put(identifier, row);
                        SearchResult existing = merged.get(identifier);
                        if (existing == null) {
                            existing = buildBaseResult(row);
                            merged.put(identifier, existing);
                        }
                        existing.setSemanticScore(Math.max(existing.getSemanticScore(), hit.getScore()));
                    }
                }
            }

            List<SearchResult> reranked = localSearchReranker.rerank(request, analysis, merged, documents);
            if (!reranked.isEmpty()) {
                ok = true;
                return DrisResponse.success(SearchResultSupport.copyResults(page(reranked, request)));
            }

            if (mode == SearchMode.SEMANTIC || mode == SearchMode.HYBRID) {
                ok = true;
                return DrisResponse.success(SearchResultSupport.copyResults(page(fullScanFallback(request, analysis), request)));
            }

            ok = true;
            return DrisResponse.success(new ArrayList<SearchResult>());
        } catch (Exception exception) {
            return DrisResponse.failure("VALIDATION_ERROR", safeMessage(exception), null);
        } finally {
            metrics.record("search", System.nanoTime() - start, ok);
        }
    }

    private List<SearchResult> fullScanFallback(SearchRequest request, SearchQueryAnalysis analysis) {
        List<SearchDocumentRow> snapshot = identifierSearchDao.findAllActive();
        Map<String, SearchResult> merged = new LinkedHashMap<String, SearchResult>();
        Map<String, SearchDocumentRow> documents = new LinkedHashMap<String, SearchDocumentRow>();
        for (SearchDocumentRow row : snapshot) {
            if (row == null || SearchTextSupport.isBlank(row.getIdentifier())) {
                continue;
            }
            if (!matchesFilters(row, analysis.getEffectiveFilters())) {
                continue;
            }
            SearchResult result = buildFallbackCandidate(row, request, analysis);
            if (result != null) {
                String identifier = SearchTextSupport.trim(row.getIdentifier());
                merged.put(identifier, result);
                documents.put(identifier, row);
            }
        }
        return localSearchReranker.rerank(request, analysis, merged, documents);
    }

    private SearchResult buildKeywordCandidate(SearchDocumentRow row, SearchRequest request, SearchQueryAnalysis analysis) {
        SearchResult result = buildBaseResult(row);
        double keywordScore = scoreCalculator.computeKeywordScore(analysis, request.getFields(), row);
        if (keywordScore <= 0.0) {
            return null;
        }
        result.setKeywordScore(keywordScore);
        return result;
    }

    private SearchResult buildFallbackCandidate(SearchDocumentRow row, SearchRequest request, SearchQueryAnalysis analysis) {
        SearchResult result = buildBaseResult(row);
        double keywordScore = scoreCalculator.computeKeywordScore(analysis, request.getFields(), row);
        double semanticScore = scoreCalculator.computeSemanticOverlap(analysis, row);
        SearchMode mode = request.getMode() == null ? SearchMode.HYBRID : request.getMode();

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

    private void mergeKeywordCandidate(Map<String, SearchResult> merged, SearchResult candidate) {
        SearchResult existing = merged.get(candidate.getIdentifier());
        if (existing == null) {
            merged.put(candidate.getIdentifier(), candidate);
            return;
        }
        existing.setKeywordScore(Math.max(existing.getKeywordScore(), candidate.getKeywordScore()));
        SearchResultSupport.fillMissing(existing, candidate);
    }

    private SearchResult buildBaseResult(SearchDocumentRow row) {
        SearchResult result = new SearchResult();
        result.setIdentifier(row.getIdentifier());
        result.setName(row.getName());
        result.setDescription(row.getDescription());
        result.setOwner(row.getOwner());
        result.setIndustry(row.getIndustry());
        result.setDataCategory(row.getDataCategory());
        result.setRegistrationType(row.getRegistrationType());
        result.setEntityType(row.getEntityType());
        result.setUpdateTime(row.getUpdateTime());
        result.setComponentType(row.getComponentType());
        result.setStorageType(row.getStorageType());
        result.setAccessMethod(row.getAccessMethod());
        result.setContentType(row.getContentType());
        result.setFormat(row.getFormat());
        result.setSource("local");
        result.setKeywordScore(0.0);
        result.setSemanticScore(0.0);
        result.setLocalScore(0.0);
        result.setFusionScore(0.0);
        result.setDisplayRank(0);
        result.setFinalScore(0.0);
        return result;
    }

    private boolean matchesFilters(SearchDocumentRow row, Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("industry".equals(key) && !eqIgnoreCase(row.getIndustry(), value)) {
                return false;
            }
            if ("status".equals(key) && !eqIgnoreCase(row.getStatus(), value)) {
                return false;
            }
            if ("entityType".equals(key) && !eqIgnoreCase(row.getEntityType(), value)) {
                return false;
            }
            if ("dataCategory".equals(key) && !eqIgnoreCase(row.getDataCategory(), value)) {
                return false;
            }
            if ("owner".equals(key) && !eqIgnoreCase(row.getOwner(), value)) {
                return false;
            }
            if ("registrationType".equals(key) && !eqIgnoreCase(row.getRegistrationType(), value)) {
                return false;
            }
            if ("componentType".equals(key) && !eqIgnoreCase(row.getComponentType(), value)) {
                return false;
            }
            if ("storageType".equals(key) && !eqIgnoreCase(row.getStorageType(), value)) {
                return false;
            }
            if ("accessMethod".equals(key) && !eqIgnoreCase(row.getAccessMethod(), value)) {
                return false;
            }
            if ("contentType".equals(key) && !eqIgnoreCase(row.getContentType(), value)) {
                return false;
            }
            if ("format".equals(key) && !eqIgnoreCase(row.getFormat(), value)) {
                return false;
            }
        }
        return true;
    }

    private List<SearchResult> page(List<SearchResult> results, SearchRequest request) {
        int from = Math.min(request.getOffset(), results.size());
        int to = Math.min(from + request.getLimit(), results.size());
        return new ArrayList<SearchResult>(results.subList(from, to));
    }

    private boolean eqIgnoreCase(String left, String right) {
        return SearchTextSupport.trim(left).equalsIgnoreCase(SearchTextSupport.trim(right));
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                ? "internal error"
                : throwable.getMessage().trim();
    }
}
