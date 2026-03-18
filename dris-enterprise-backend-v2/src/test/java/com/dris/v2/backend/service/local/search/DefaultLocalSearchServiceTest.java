package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.IdentifierSearchDao;
import com.dris.v2.backend.dao.SearchDocumentRow;
import com.dris.v2.backend.metrics.BackendOperationMetrics;
import com.dris.v2.backend.vector.VectorIndexGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLocalSearchServiceTest {

    @Test
    void search_shouldApplyInferredFiltersAndRanks() {
        IdentifierSearchDao dao = mock(IdentifierSearchDao.class);
        VectorIndexGateway vectorIndexGateway = mock(VectorIndexGateway.class);
        BackendOperationMetrics metrics = mock(BackendOperationMetrics.class);
        DefaultLocalSearchService service = new DefaultLocalSearchService(
                dao,
                vectorIndexGateway,
                metrics,
                new DefaultSearchQueryAnalyzer(new DefaultSearchSynonymRegistry()),
                new SearchScoreCalculator(new SearchDocumentTextBuilder()),
                new LocalSearchReranker(new SearchScoreCalculator(new SearchDocumentTextBuilder()))
        );

        when(dao.searchFuzzy(anyList(), anyList(), anyMap(), anyInt(), anyInt())).thenReturn(Arrays.asList(
                row("id-1", "Electric Vehicle Population Data", "csv"),
                row("id-2", "Vehicle directory", "csv")
        ));
        when(vectorIndexGateway.search(org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(Collections.emptyList());

        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("查找电动汽车 csv 数据资源");
        request.setLimit(10);

        DrisResponse<List<SearchResult>> response = service.search(request);

        assertTrue(response.isOk());
        assertEquals(2, response.getData().size());
        assertEquals(1, response.getData().get(0).getDisplayRank());
        assertTrue(response.getData().get(0).getFinalScore() >= response.getData().get(1).getFinalScore());

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(dao).searchFuzzy(anyList(), anyList(), captor.capture(), anyInt(), anyInt());
        assertEquals("csv", captor.getValue().get("format"));
    }

    private SearchDocumentRow row(String identifier, String name, String format) {
        SearchDocumentRow row = new SearchDocumentRow();
        row.setIdentifier(identifier);
        row.setName(name);
        row.setDescription(name + " description");
        row.setApplicationScenario("research");
        row.setFormat(format);
        row.setAccessMethod("download");
        row.setComponentType("resource");
        row.setLocator("https://example.com/" + identifier + "." + format);
        return row;
    }
}
