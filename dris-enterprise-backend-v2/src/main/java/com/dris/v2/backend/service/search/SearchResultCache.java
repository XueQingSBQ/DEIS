package com.dris.v2.backend.service.search;

import com.dris.v2.backend.common.cache.LocalTtlCache;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.contract.search.SearchResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class SearchResultCache {
    private final LocalTtlCache<String, List<SearchResult>> cache;

    public SearchResultCache(BackendV2Properties properties) {
        boolean enabled = properties != null && properties.isSearchCacheEnabled();
        long ttlMs = properties == null ? 30000L : properties.getSearchCacheTtlMs();
        int maxEntries = properties == null ? 1000 : properties.getSearchCacheMaxEntries();
        this.cache = new LocalTtlCache<String, List<SearchResult>>(enabled, ttlMs, maxEntries, new Function<List<SearchResult>, List<SearchResult>>() {
            @Override
            public List<SearchResult> apply(List<SearchResult> value) {
                return SearchResultSupport.copyResults(value);
            }
        });
    }

    public List<SearchResult> get(String key) {
        return cache.get(key);
    }

    public void put(String key, List<SearchResult> value) {
        if (value == null) {
            return;
        }
        cache.put(key, value);
    }

    public void evictAll() {
        cache.clear();
    }
}
