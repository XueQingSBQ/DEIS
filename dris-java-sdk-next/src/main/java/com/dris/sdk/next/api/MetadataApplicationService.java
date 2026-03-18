package com.dris.sdk.next.api;

import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.request.PatchMetadataRequest;
import com.dris.sdk.next.request.UpdateMetadataRequest;
import com.dris.sdk.next.result.BatchResult;

import java.util.List;

public interface MetadataApplicationService {
    IdentifierMetadata get(String identifier);

    BatchResult<IdentifierMetadata> getBatch(List<String> identifiers);

    IdentifierMetadata update(UpdateMetadataRequest request);

    IdentifierMetadata patch(PatchMetadataRequest request);
}