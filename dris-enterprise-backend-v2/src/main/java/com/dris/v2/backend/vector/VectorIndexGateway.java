package com.dris.v2.backend.vector;

import com.dris.sdk.next.domain.IdentifierMetadata;

import java.util.List;

public interface VectorIndexGateway {
    boolean upsertBatch(List<IdentifierMetadata> metadataList);

    boolean deleteBatch(List<String> identifiers);

    List<VectorSearchHit> search(String queryText, int topK);
}
