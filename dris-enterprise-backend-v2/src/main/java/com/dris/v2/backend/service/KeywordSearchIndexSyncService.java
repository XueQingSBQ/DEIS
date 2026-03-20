package com.dris.v2.backend.service;

import com.dris.v2.backend.contract.system.KeywordIndexSyncResult;
import com.dris.v2.backend.dao.IdentifierSearchDao;
import com.dris.v2.backend.dao.SearchDocumentRow;
import com.dris.v2.backend.service.local.search.KeywordSearchGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class KeywordSearchIndexSyncService {
    private static final Logger log = LoggerFactory.getLogger(KeywordSearchIndexSyncService.class);

    private final KeywordSearchGateway keywordSearchGateway;
    private final IdentifierSearchDao identifierSearchDao;
    private final FederatedSearchService federatedSearchService;

    public KeywordSearchIndexSyncService(
            KeywordSearchGateway keywordSearchGateway,
            IdentifierSearchDao identifierSearchDao,
            FederatedSearchService federatedSearchService
    ) {
        this.keywordSearchGateway = keywordSearchGateway;
        this.identifierSearchDao = identifierSearchDao;
        this.federatedSearchService = federatedSearchService;
    }

    public void syncUpsertQuietly(String identifier) {
        syncUpsertQuietly(identifier == null ? null : Collections.singletonList(identifier));
    }

    public void syncUpsertQuietly(List<String> identifiers) {
        List<String> safeIdentifiers = sanitizeIdentifiers(identifiers);
        if (safeIdentifiers.isEmpty()) {
            return;
        }
        try {
            Map<String, SearchDocumentRow> rows = identifierSearchDao.findActiveByIdentifiers(safeIdentifiers);
            if (rows == null || rows.isEmpty()) {
                return;
            }
            KeywordIndexSyncResult result = keywordSearchGateway.upsertBatch(new ArrayList<SearchDocumentRow>(rows.values()));
            clearCacheIfChanged(result);
        } catch (RuntimeException exception) {
            log.warn("keyword index upsert sync failed: {}", exception.getMessage());
        }
    }

    public void syncDeleteQuietly(String identifier) {
        syncDeleteQuietly(identifier == null ? null : Collections.singletonList(identifier));
    }

    public void syncDeleteQuietly(List<String> identifiers) {
        List<String> safeIdentifiers = sanitizeIdentifiers(identifiers);
        if (safeIdentifiers.isEmpty()) {
            return;
        }
        try {
            KeywordIndexSyncResult result = keywordSearchGateway.deleteBatch(safeIdentifiers);
            clearCacheIfChanged(result);
        } catch (RuntimeException exception) {
            log.warn("keyword index delete sync failed: {}", exception.getMessage());
        }
    }

    private void clearCacheIfChanged(KeywordIndexSyncResult result) {
        if (result == null || result.isSkipped()) {
            return;
        }
        if (result.getIndexed() > 0 || result.getDeleted() > 0) {
            federatedSearchService.clearSearchCache();
        }
    }

    private List<String> sanitizeIdentifiers(List<String> identifiers) {
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        if (identifiers == null) {
            return new ArrayList<String>();
        }
        for (String identifier : identifiers) {
            String value = trim(identifier);
            if (!value.isEmpty()) {
                unique.add(value);
            }
        }
        return new ArrayList<String>(unique);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
