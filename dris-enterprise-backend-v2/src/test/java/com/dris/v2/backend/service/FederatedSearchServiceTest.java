package com.dris.v2.backend.service;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.service.search.SearchResultCache;
import com.dris.v2.backend.service.local.search.LocalSearchService;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.contract.DrisResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class FederatedSearchServiceTest {
    @Mock
    private LocalSearchService localSearchService;

    @Test
    void search_nullRequest_shouldThrow() {
        FederatedSearchService service = createService(defaultProperties());
        assertThrows(BusinessException.class, () -> service.search(null));
    }

    @Test
    void search_validRequest_shouldDelegate() {
        BackendV2Properties properties = defaultProperties();
        properties.setSearchCacheEnabled(false);
        properties.setSearchRateLimitEnabled(false);
        properties.setSearchCircuitBreakerEnabled(false);
        FederatedSearchService service = createService(properties);

        SearchRequest request = TestSamples.searchRequest();
        when(localSearchService.search(request)).thenReturn(DrisResponse.success(Collections.<SearchResult>emptyList()));

        DrisResponse<java.util.List<SearchResult>> out = service.search(request);

        assertTrue(out.isOk());
        verify(localSearchService).search(request);
    }

    @Test
    void search_cacheEnabled_shouldHitCache() {
        BackendV2Properties properties = defaultProperties();
        properties.setSearchCacheEnabled(true);
        properties.setSearchCacheTtlMs(60000L);
        properties.setSearchRateLimitEnabled(false);
        properties.setSearchCircuitBreakerEnabled(false);
        FederatedSearchService service = createService(properties);

        SearchRequest request = TestSamples.searchRequest();
        List<SearchResult> rows = new ArrayList<SearchResult>();
        SearchResult row = new SearchResult();
        row.setIdentifier("1100ABCD0211202603051234567895");
        rows.add(row);
        when(localSearchService.search(request)).thenReturn(DrisResponse.success(rows));

        DrisResponse<List<SearchResult>> first = service.search(request);
        DrisResponse<List<SearchResult>> second = service.search(request);

        assertTrue(first.isOk());
        assertTrue(second.isOk());
        assertEquals(1, second.getData().size());
        verify(localSearchService, times(1)).search(request);
    }

    @Test
    void search_rateLimitExceeded_shouldThrow() {
        BackendV2Properties properties = defaultProperties();
        properties.setSearchCacheEnabled(false);
        properties.setSearchRateLimitEnabled(true);
        properties.setSearchRateLimitQps(1);
        properties.setSearchRateLimitBurst(1);
        properties.setSearchCircuitBreakerEnabled(false);
        FederatedSearchService service = createService(properties);

        SearchRequest request = TestSamples.searchRequest();
        when(localSearchService.search(request)).thenReturn(DrisResponse.success(Collections.<SearchResult>emptyList()));

        service.search(request);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.search(request));
        assertEquals("RATE_LIMITED", ex.getCode());
    }

    @Test
    void search_circuitOpen_shouldBlockAfterFailures() {
        BackendV2Properties properties = defaultProperties();
        properties.setSearchCacheEnabled(false);
        properties.setSearchRateLimitEnabled(false);
        properties.setSearchCircuitBreakerEnabled(true);
        properties.setSearchCircuitFailureThreshold(2);
        properties.setSearchCircuitOpenMs(1000L);
        FederatedSearchService service = createService(properties);

        SearchRequest request = TestSamples.searchRequest();
        when(localSearchService.search(request)).thenReturn(DrisResponse.failure("UPSTREAM_ERROR", "boom", null));

        service.search(request);
        service.search(request);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.search(request));
        assertEquals("CIRCUIT_OPEN", ex.getCode());
    }

    @Test
    void search_circuitHalfOpen_shouldRecoverAfterWindow() throws Exception {
        BackendV2Properties properties = defaultProperties();
        properties.setSearchCacheEnabled(false);
        properties.setSearchRateLimitEnabled(false);
        properties.setSearchCircuitBreakerEnabled(true);
        properties.setSearchCircuitFailureThreshold(1);
        properties.setSearchCircuitOpenMs(80L);
        properties.setSearchCircuitHalfOpenPermits(1);
        FederatedSearchService service = createService(properties);

        SearchRequest request = TestSamples.searchRequest();
        when(localSearchService.search(request))
                .thenReturn(DrisResponse.failure("UPSTREAM_ERROR", "boom", null))
                .thenReturn(DrisResponse.success(Collections.<SearchResult>emptyList()));

        service.search(request);
        assertThrows(BusinessException.class, () -> service.search(request));
        Thread.sleep(120L);
        DrisResponse<List<SearchResult>> recovered = service.search(request);
        assertTrue(recovered.isOk());
    }

    private FederatedSearchService createService(BackendV2Properties properties) {
        return new FederatedSearchService(localSearchService, properties, new SearchResultCache(properties));
    }

    private BackendV2Properties defaultProperties() {
        return new BackendV2Properties();
    }
}
