package com.dris.sdk.next.api;

import com.dris.sdk.next.application.DefaultDrisSdk;
import com.dris.sdk.next.application.DefaultIdentifierApplicationService;
import com.dris.sdk.next.application.DefaultMetadataApplicationService;
import com.dris.sdk.next.application.DefaultResourceApplicationService;
import com.dris.sdk.next.infrastructure.IdentifierCredentialGenerator;
import com.dris.sdk.next.infrastructure.TimeProvider;
import com.dris.sdk.next.infrastructure.TransactionRunner;
import com.dris.sdk.next.repository.DigitalObjectRepository;
import com.dris.sdk.next.repository.RegistrationDictionary;

public final class DrisSdkFactory {
    private DrisSdkFactory() {
    }

    public static DrisSdk compose(
            IdentifierApplicationService identifierService,
            MetadataApplicationService metadataService,
            ResourceApplicationService resourceService
    ) {
        return new DefaultDrisSdk(identifierService, metadataService, resourceService);
    }

    public static DrisSdk compose(
            DigitalObjectRepository repository,
            RegistrationDictionary registrationDictionary,
            TransactionRunner transactionRunner,
            TimeProvider timeProvider,
            IdentifierCredentialGenerator credentialGenerator
    ) {
        IdentifierApplicationService identifierService = new DefaultIdentifierApplicationService(
                repository,
                registrationDictionary,
                transactionRunner,
                timeProvider,
                credentialGenerator
        );
        MetadataApplicationService metadataService = new DefaultMetadataApplicationService(
                repository,
                registrationDictionary,
                transactionRunner,
                timeProvider
        );
        ResourceApplicationService resourceService = new DefaultResourceApplicationService(
                repository,
                transactionRunner,
                timeProvider
        );
        return compose(identifierService, metadataService, resourceService);
    }
}