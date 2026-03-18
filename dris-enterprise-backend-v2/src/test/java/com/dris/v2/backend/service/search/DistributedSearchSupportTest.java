package com.dris.v2.backend.service.search;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.model.SearchForwardHop;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistributedSearchSupportTest {
    @Test
    void mergeByRrf_shouldRespectPerSourceRankingBeforeCrossNodeFusion() throws Exception {
        DistributedSearchSupport support = new DistributedSearchSupport(null, new BackendV2Properties(), null, null, null, null);

        List<SearchResult> merged = invokeMergeByRrf(
                support,
                Arrays.asList(
                        hit("node-a-first", "1100AAAA", 0.20, 0.20, 0.0),
                        hit("node-a-second", "1100AAAA", 0.19, 0.19, 0.0),
                        hit("node-b-first", "2200BBBB", 0.95, 0.95, 0.0)
                )
        );

        assertEquals("node-b-first", merged.get(0).getIdentifier());
        assertEquals("node-a-first", merged.get(1).getIdentifier());
        assertEquals("node-a-second", merged.get(2).getIdentifier());
    }

    @Test
    void mergeByRrf_singleSourceShouldPreserveOriginalLocalScores() throws Exception {
        DistributedSearchSupport support = new DistributedSearchSupport(null, new BackendV2Properties(), null, null, null, null);

        List<SearchResult> merged = invokeMergeByRrf(
                support,
                Arrays.asList(
                        hit("best-local", "1100ABCD", 0.91, 0.91, 0.0),
                        hit("other-local", "1100ABCD", 0.32, 0.32, 0.0)
                )
        );

        assertEquals("best-local", merged.get(0).getIdentifier());
        assertEquals(0.91, merged.get(0).getFinalScore(), 0.000001);
        assertEquals("other-local", merged.get(1).getIdentifier());
    }

    @Test
    void enqueueIterativeNextHops_shouldDeduplicateAndRespectMaxFanout() throws Exception {
        DistributedSearchSupport support = new DistributedSearchSupport(null, new BackendV2Properties(), null, null, null, null);
        Method method = DistributedSearchSupport.class.getDeclaredMethod(
                "enqueueIterativeNextHops",
                List.class,
                LinkedHashSet.class,
                List.class,
                int.class,
                int.class
        );
        method.setAccessible(true);

        List<Object> queue = new ArrayList<Object>();
        LinkedHashSet<String> scheduledUrls = new LinkedHashSet<String>();
        scheduledUrls.add("grpc://node-a");
        List<SearchForwardHop> nextHops = Arrays.asList(
                new SearchForwardHop("node-a", "grpc://node-a"),
                new SearchForwardHop("node-b", "grpc://node-b"),
                new SearchForwardHop("node-c", "grpc://node-c")
        );

        method.invoke(support, queue, scheduledUrls, nextHops, 3, 1);

        assertEquals(1, queue.size());
        assertEquals("node-b", readField(queue.get(0), "nodeCode"));
        assertEquals("grpc://node-b", readField(queue.get(0), "drirpUrl"));
        assertEquals(3, ((Integer) readField(queue.get(0), "ttl")).intValue());
        assertEquals(2, scheduledUrls.size());
    }

    @SuppressWarnings("unchecked")
    private List<SearchResult> invokeMergeByRrf(DistributedSearchSupport support, List<SearchResult> hits) throws Exception {
        Method method = DistributedSearchSupport.class.getDeclaredMethod("mergeByRrf", List.class);
        method.setAccessible(true);
        return (List<SearchResult>) method.invoke(support, hits);
    }

    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private SearchResult hit(String identifier, String source, double finalScore, double keywordScore, double semanticScore) {
        SearchResult result = new SearchResult();
        result.setIdentifier(identifier);
        result.setSource(source);
        result.setFinalScore(finalScore);
        result.setKeywordScore(keywordScore);
        result.setSemanticScore(semanticScore);
        return result;
    }
}