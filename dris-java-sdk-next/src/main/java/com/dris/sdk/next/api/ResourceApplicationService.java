package com.dris.sdk.next.api;

import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.request.BindResourceRequest;
import com.dris.sdk.next.request.RemoveResourceRequest;
import com.dris.sdk.next.result.BatchResult;

import java.util.List;

public interface ResourceApplicationService {
    Resource get(String identifier);

    BatchResult<Resource> getBatch(List<String> identifiers);

    Resource bind(BindResourceRequest request);

    void remove(RemoveResourceRequest request);
}