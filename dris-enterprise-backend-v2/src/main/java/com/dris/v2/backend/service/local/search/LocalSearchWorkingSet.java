package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.search.SearchResult;
import com.dris.v2.backend.dao.SearchDocumentRow;
import com.dris.v2.backend.service.search.SearchResultSupport;

import java.util.LinkedHashMap;
import java.util.Map;

class LocalSearchWorkingSet {
    private final Map<String, SearchResult> merged = new LinkedHashMap<String, SearchResult>();
    private final Map<String, SearchDocumentRow> documents = new LinkedHashMap<String, SearchDocumentRow>();

    Map<String, SearchResult> getMerged() {
        return merged;
    }

    Map<String, SearchDocumentRow> getDocuments() {
        return documents;
    }

    void putDocument(SearchDocumentRow row) {
        if (row == null || SearchTextSupport.isBlank(row.getIdentifier())) {
            return;
        }
        documents.put(SearchTextSupport.trim(row.getIdentifier()), row);
    }

    void addKeywordCandidate(SearchResult candidate) {
        if (candidate == null || SearchTextSupport.isBlank(candidate.getIdentifier())) {
            return;
        }
        String identifier = SearchTextSupport.trim(candidate.getIdentifier());
        SearchResult existing = merged.get(identifier);
        if (existing == null) {
            merged.put(identifier, candidate);
            return;
        }
        existing.setKeywordScore(Math.max(existing.getKeywordScore(), candidate.getKeywordScore()));
        SearchResultSupport.fillMissing(existing, candidate);
    }

    SearchResult getOrCreate(SearchDocumentRow row, SearchResultFactory resultFactory) {
        if (row == null || SearchTextSupport.isBlank(row.getIdentifier())) {
            return null;
        }
        String identifier = SearchTextSupport.trim(row.getIdentifier());
        SearchResult existing = merged.get(identifier);
        if (existing != null) {
            return existing;
        }
        SearchResult created = resultFactory.fromRow(row);
        merged.put(identifier, created);
        return created;
    }
}
