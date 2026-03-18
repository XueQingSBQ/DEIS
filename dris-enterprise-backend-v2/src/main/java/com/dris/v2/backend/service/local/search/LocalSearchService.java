package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchRequest;
import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.contract.DrisResponse;

import java.util.List;

public interface LocalSearchService {
    DrisResponse<List<SearchResult>> search(SearchRequest request);
}