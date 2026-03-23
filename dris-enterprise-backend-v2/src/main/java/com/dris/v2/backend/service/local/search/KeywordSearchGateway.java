package com.dris.v2.backend.service.local.search;

import com.dris.v2.backend.contract.system.KeywordIndexSyncResult;
import com.dris.v2.backend.dao.SearchDocumentRow;

import java.util.List;
import java.util.Map;

public interface KeywordSearchGateway {
    String engine();

    List<SearchDocumentRow> search(
            List<String> tokens,
            List<String> fields,
            Map<String, String> filters,
            int limit,
            int offset
    );

    KeywordIndexSyncResult initializeIndex();

    KeywordIndexSyncResult rebuildIndex(List<SearchDocumentRow> documents);

    KeywordIndexSyncResult upsertBatch(List<SearchDocumentRow> documents);

    KeywordIndexSyncResult deleteBatch(List<String> identifiers);
}
