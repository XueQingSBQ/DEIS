package com.dris.v2.backend.service.search;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.search.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchResultCacheTest {

    @Test
    void get_shouldReturnDefensiveCopy() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setSearchCacheEnabled(true);
        properties.setSearchCacheTtlMs(60000L);
        properties.setSearchCacheMaxEntries(8);
        SearchResultCache cache = new SearchResultCache(properties);

        SearchResult source = new SearchResult();
        source.setIdentifier("id-1");
        source.setName("before");
        cache.put("k1", Collections.singletonList(source));

        List<SearchResult> first = cache.get("k1");
        assertNotNull(first);
        first.get(0).setName("mutated");

        List<SearchResult> second = cache.get("k1");
        assertEquals("before", second.get(0).getName());
    }

    @Test
    void put_shouldEvictOldestEntryWhenOverflow() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setSearchCacheEnabled(true);
        properties.setSearchCacheTtlMs(60000L);
        properties.setSearchCacheMaxEntries(1);
        SearchResultCache cache = new SearchResultCache(properties);

        cache.put("k1", Collections.singletonList(result("id-1")));
        cache.put("k2", Collections.singletonList(result("id-2")));

        assertNull(cache.get("k1"));
        assertEquals("id-2", cache.get("k2").get(0).getIdentifier());
    }

    private SearchResult result(String identifier) {
        SearchResult result = new SearchResult();
        result.setIdentifier(identifier);
        result.setName(identifier);
        return result;
    }
}