package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchRequest;

public interface SearchQueryAnalyzer {
    SearchQueryAnalysis analyze(SearchRequest request);
}
