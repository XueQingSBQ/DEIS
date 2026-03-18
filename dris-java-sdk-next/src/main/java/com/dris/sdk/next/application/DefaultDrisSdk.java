package com.dris.sdk.next.application;

import com.dris.sdk.next.api.DrisSdk;
import com.dris.sdk.next.api.IdentifierApplicationService;
import com.dris.sdk.next.api.MetadataApplicationService;
import com.dris.sdk.next.api.ResourceApplicationService;

public class DefaultDrisSdk implements DrisSdk {
    private final IdentifierApplicationService identifierService;
    private final MetadataApplicationService metadataService;
    private final ResourceApplicationService resourceService;

    public DefaultDrisSdk(
            IdentifierApplicationService identifierService,
            MetadataApplicationService metadataService,
            ResourceApplicationService resourceService
    ) {
        this.identifierService = identifierService;
        this.metadataService = metadataService;
        this.resourceService = resourceService;
    }

    @Override
    public IdentifierApplicationService identifiers() {
        return identifierService;
    }

    @Override
    public MetadataApplicationService metadata() {
        return metadataService;
    }

    @Override
    public ResourceApplicationService resources() {
        return resourceService;
    }

    @Override
    public void close() {
    }
}