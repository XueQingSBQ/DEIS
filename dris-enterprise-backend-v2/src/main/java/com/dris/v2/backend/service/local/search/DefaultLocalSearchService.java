package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.metrics.BackendOperationMetrics;
import com.dris.v2.backend.service.search.SearchResultSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultLocalSearchService implements LocalSearchService {
    private final BackendOperationMetrics metrics;
    private final SearchQueryAnalyzer queryAnalyzer;
    private final LocalSearchReranker localSearchReranker;
    private final LocalSearchCandidateAssembler candidateAssembler;

    public DefaultLocalSearchService(
            BackendOperationMetrics metrics,
            SearchQueryAnalyzer queryAnalyzer,
            LocalSearchReranker localSearchReranker,
            LocalSearchCandidateAssembler candidateAssembler
    ) {
        this.metrics = metrics;
        this.queryAnalyzer = queryAnalyzer;
        this.localSearchReranker = localSearchReranker;
        this.candidateAssembler = candidateAssembler;
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

            com.dris.v2.backend.contract.search.SearchMode mode = candidateAssembler.resolveMode(request);
            SearchQueryAnalysis analysis = queryAnalyzer.analyze(request);
            int candidateLimit = candidateAssembler.resolveCandidateLimit(request);
            LocalSearchWorkingSet workingSet = candidateAssembler.collect(request, analysis, mode, candidateLimit);

            List<SearchResult> reranked = localSearchReranker.rerank(
                    request,
                    analysis,
                    workingSet.getMerged(),
                    workingSet.getDocuments()
            );
            if (!reranked.isEmpty()) {
                ok = true;
                return DrisResponse.success(SearchResultSupport.copyResults(page(reranked, request)));
            }

            if (mode == com.dris.v2.backend.contract.search.SearchMode.SEMANTIC
                    || mode == com.dris.v2.backend.contract.search.SearchMode.HYBRID) {
                ok = true;
                LocalSearchWorkingSet fallback = candidateAssembler.collectFallback(request, analysis, mode);
                List<SearchResult> fallbackResults = localSearchReranker.rerank(
                        request,
                        analysis,
                        fallback.getMerged(),
                        fallback.getDocuments()
                );
                return DrisResponse.success(SearchResultSupport.copyResults(page(fallbackResults, request)));
            }

            ok = true;
            return DrisResponse.success(new ArrayList<SearchResult>());
        } catch (Exception exception) {
            return DrisResponse.failure("VALIDATION_ERROR", safeMessage(exception), null);
        } finally {
            metrics.record("search", System.nanoTime() - start, ok);
        }
    }

    private List<SearchResult> page(List<SearchResult> results, SearchRequest request) {
        int from = Math.min(request.getOffset(), results.size());
        int to = Math.min(from + request.getLimit(), results.size());
        return new ArrayList<SearchResult>(results.subList(from, to));
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null || throwable.getMessage().trim().isEmpty()
                ? "internal error"
                : throwable.getMessage().trim();
    }
}
