package com.dris.v2.backend.sdknext;

import com.dris.v2.backend.contract.registration.RegistrationRequest;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;

import java.util.List;
import java.util.Map;

public interface LocalDrisKernelFacade {
    String register(RegistrationRequest request);

    List<String> registerBatch(List<RegistrationRequest> requests);

    boolean validateIdentifier(String identifier);

    boolean existsIdentifier(String identifier);

    IdentifierMetadata getMetadata(String identifier);

    List<IdentifierMetadata> getMetadataBatch(List<String> identifiers);

    DigitalObject getDigitalObject(String identifier);

    boolean updateMetadata(String identifier, IdentifierMetadata metadata);

    boolean patchMetadata(String identifier, Map<String, String> fields);

    boolean deleteIdentifier(String identifier);

    Resource getResource(String identifier);

    boolean upsertResource(String identifier, Resource resource, boolean forceReplaceType);

    boolean removeResource(String identifier);
}
