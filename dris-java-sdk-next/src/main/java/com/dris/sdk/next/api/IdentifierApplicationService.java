package com.dris.sdk.next.api;

import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierCode;
import com.dris.sdk.next.request.DeleteIdentifierRequest;
import com.dris.sdk.next.request.RegisterIdentifierRequest;
import com.dris.sdk.next.result.BatchResult;

import java.util.List;

public interface IdentifierApplicationService {
    String register(RegisterIdentifierRequest request);

    BatchResult<String> registerBatch(List<RegisterIdentifierRequest> requests);

    boolean validate(String identifier);

    boolean exists(String identifier);

    IdentifierCode parse(String identifier);

    DigitalObject getDigitalObject(String identifier);

    BatchResult<DigitalObject> getDigitalObjects(List<String> identifiers);

    void delete(DeleteIdentifierRequest request);
}