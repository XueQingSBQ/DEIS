package com.dris.v2.backend.service;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.service.local.search.LocalSearchService;
import com.dris.v2.backend.service.search.SearchCircuitBreaker;
import com.dris.v2.backend.service.search.SearchRateLimiter;
import com.dris.v2.backend.service.search.SearchResultCache;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.contract.DrisResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class FederatedSearchService {
    private final LocalSearchService localSearchService;
    private final BackendV2Properties properties;
    private final SearchResultCache searchResultCache;
    private final SearchRateLimiter rateLimiter;
    private final SearchCircuitBreaker circuitBreaker;

    public FederatedSearchService(
            LocalSearchService localSearchService,
            BackendV2Properties properties,
            SearchResultCache searchResultCache
    ) {
        this.localSearchService = localSearchService;
        this.properties = properties == null ? new BackendV2Properties() : properties;
        this.searchResultCache = searchResultCache;
        this.rateLimiter = new SearchRateLimiter(this.properties.getSearchRateLimitQps(), this.properties.getSearchRateLimitBurst());
        this.circuitBreaker = new SearchCircuitBreaker(
                this.properties.getSearchCircuitFailureThreshold(),
                this.properties.getSearchCircuitOpenMs(),
                this.properties.getSearchCircuitHalfOpenPermits()
        );
    }

    public DrisResponse<List<SearchResult>> search(SearchRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            throw new BusinessException("VALIDATION_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        if (properties.isSearchCircuitBreakerEnabled() && !circuitBreaker.allowRequest()) {
            throw new BusinessException("CIRCUIT_OPEN", "search is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (properties.isSearchRateLimitEnabled() && !rateLimiter.tryAcquire()) {
            throw new BusinessException("RATE_LIMITED", "search request rate exceeded", HttpStatus.TOO_MANY_REQUESTS);
        }

        String cacheKey = buildCacheKey(request);
        if (searchResultCache != null) {
            List<SearchResult> cached = searchResultCache.get(cacheKey);
            if (cached != null) {
                if (properties.isSearchCircuitBreakerEnabled()) {
                    circuitBreaker.onSuccess();
                }
                return DrisResponse.success(cached);
            }
        }

        DrisResponse<List<SearchResult>> response;
        try {
            response = localSearchService.search(request);
        } catch (Exception e) {
            if (properties.isSearchCircuitBreakerEnabled()) {
                circuitBreaker.onFailure();
            }
            throw new BusinessException("UPSTREAM_ERROR", "search upstream call failed: " + safeMessage(e), HttpStatus.BAD_GATEWAY);
        }
        if (response == null) {
            if (properties.isSearchCircuitBreakerEnabled()) {
                circuitBreaker.onFailure();
            }
            return DrisResponse.failure("UPSTREAM_ERROR", "search response is null", null);
        }
        if (response.isOk()) {
            if (properties.isSearchCircuitBreakerEnabled()) {
                circuitBreaker.onSuccess();
            }
            if (searchResultCache != null) {
                searchResultCache.put(cacheKey, response.getData());
            }
            return response;
        }
        if (properties.isSearchCircuitBreakerEnabled() && shouldCountFailure(response.getCode())) {
            circuitBreaker.onFailure();
        }
        return response;
    }

    public void clearSearchCache() {
        if (searchResultCache != null) {
            searchResultCache.evictAll();
        }
    }

    private String buildCacheKey(SearchRequest request) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("q=").append(normalize(request.getNaturalQuery())).append('|');
        sb.append("mode=").append(request.getMode() == null ? "" : request.getMode().name()).append('|');
        sb.append("limit=").append(request.getLimit()).append('|');
        sb.append("offset=").append(request.getOffset()).append('|');
        sb.append("sw=").append(request.getSemanticWeight()).append('|');
        sb.append("kw=").append(joinSorted(request.getKeywords())).append('|');
        sb.append("fields=").append(joinSorted(request.getFields())).append('|');
        sb.append("filters=").append(joinFilters(request.getFilters()));
        return sb.toString();
    }

    private String joinSorted(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> normalized = new ArrayList<String>();
        for (String value : values) {
            String item = normalize(value);
            if (!item.isEmpty()) {
                normalized.add(item);
            }
        }
        Collections.sort(normalized);
        return String.join(",", normalized);
    }

    private String joinFilters(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        Map<String, String> ordered = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            ordered.put(normalize(entry.getKey()), normalize(entry.getValue()));
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private boolean shouldCountFailure(String code) {
        String c = normalize(code).toUpperCase(Locale.ROOT);
        if ("".equals(c)) {
            return true;
        }
        if ("VALIDATION_ERROR".equals(c) || "NOT_FOUND".equals(c) || "CONFLICT".equals(c)) {
            return false;
        }
        return true;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safeMessage(Exception e) {
        if (e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()) {
            return "unknown error";
        }
        return e.getMessage();
    }
}