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
        KeywordSearchGateway keywordSearchGateway = new MysqlKeywordSearchGateway(dao);
        VectorIndexGateway vectorIndexGateway = mock(VectorIndexGateway.class);
        BackendOperationMetrics metrics = mock(BackendOperationMetrics.class);
        DefaultLocalSearchService service = newService(dao, keywordSearchGateway, vectorIndexGateway, metrics);

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

    @Test
    void search_shouldMatchAdditionalAttributesByDefault() {
        IdentifierSearchDao dao = mock(IdentifierSearchDao.class);
        KeywordSearchGateway keywordSearchGateway = new MysqlKeywordSearchGateway(dao);
        VectorIndexGateway vectorIndexGateway = mock(VectorIndexGateway.class);
        BackendOperationMetrics metrics = mock(BackendOperationMetrics.class);
        DefaultLocalSearchService service = newService(dao, keywordSearchGateway, vectorIndexGateway, metrics);

        SearchDocumentRow row = row("id-attrs", "Flood Bulletin", "pdf");
        row.setAdditionalAttributesText("{\"region\":\"江西\",\"keywords\":\"新干县 山洪沟治理\"}");
        when(dao.searchFuzzy(anyList(), anyList(), anyMap(), anyInt(), anyInt())).thenReturn(Collections.singletonList(row));
        when(vectorIndexGateway.search(org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(Collections.emptyList());

        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("新干县 山洪沟治理");
        request.setLimit(10);

        DrisResponse<List<SearchResult>> response = service.search(request);

        assertTrue(response.isOk());
        assertEquals(1, response.getData().size());
        assertEquals("id-attrs", response.getData().get(0).getIdentifier());
        assertTrue(response.getData().get(0).getKeywordScore() > 0.0);
    }

    @Test
    void search_shouldPreferMatchedThemeWithinDocuments() {
        IdentifierSearchDao dao = mock(IdentifierSearchDao.class);
        KeywordSearchGateway keywordSearchGateway = new MysqlKeywordSearchGateway(dao);
        VectorIndexGateway vectorIndexGateway = mock(VectorIndexGateway.class);
        BackendOperationMetrics metrics = mock(BackendOperationMetrics.class);
        DefaultLocalSearchService service = newService(dao, keywordSearchGateway, vectorIndexGateway, metrics);

        SearchDocumentRow standard = row("id-standard", "山洪灾害分析评价技术要求", "pdf");
        standard.setAdditionalAttributesText("{\"column\":\"规范文件\",\"keywords\":[\"规范文件\",\"技术要求\",\"分析评价\"]}");
        SearchDocumentRow dynamic = row("id-dynamic", "国家防办督查山洪灾害防治项目建设情况", "docx");
        dynamic.setAdditionalAttributesText("{\"column\":\"工作动态\",\"keywords\":[\"工作动态\",\"督查\",\"项目建设\"]}");

        when(dao.searchFuzzy(anyList(), anyList(), anyMap(), anyInt(), anyInt())).thenReturn(Arrays.asList(dynamic, standard));
        when(vectorIndexGateway.search(org.mockito.ArgumentMatchers.anyString(), anyInt())).thenReturn(Collections.emptyList());

        SearchRequest request = new SearchRequest();
        request.setNaturalQuery("检索山洪灾害分析评价相关规范文件和技术要求");
        request.setKeywords(Arrays.asList("山洪灾害", "分析评价", "规范文件", "技术要求"));
        request.setLimit(10);

        DrisResponse<List<SearchResult>> response = service.search(request);

        assertTrue(response.isOk());
        assertEquals(2, response.getData().size());
        assertEquals("id-standard", response.getData().get(0).getIdentifier());
        assertEquals("document", response.getData().get(0).getEntityType());
    }

    private DefaultLocalSearchService newService(
            IdentifierSearchDao dao,
            KeywordSearchGateway keywordSearchGateway,
            VectorIndexGateway vectorIndexGateway,
            BackendOperationMetrics metrics
    ) {
        SearchScoreCalculator scoreCalculator = new SearchScoreCalculator(new SearchDocumentTextBuilder());
        return new DefaultLocalSearchService(
                metrics,
                new DefaultSearchQueryAnalyzer(new DefaultSearchSynonymRegistry()),
                new LocalSearchReranker(scoreCalculator),
                new LocalSearchCandidateAssembler(
                        keywordSearchGateway,
                        dao,
                        vectorIndexGateway,
                        scoreCalculator,
                        new SearchResultFactory(),
                        new SearchDocumentFilter()
                )
        );
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
        row.setEntityType("document");
        row.setLocator("https://example.com/" + identifier + "." + format);
        return row;
    }
}
