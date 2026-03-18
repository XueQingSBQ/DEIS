package com.dris.v2.backend.service.local.search;

import java.util.List;

public interface SearchSynonymRegistry {
    List<String> lookup(String term);

    List<String> allTerms();
}
