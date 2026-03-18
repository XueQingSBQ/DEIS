package com.dris.sdk.next.application;

import com.dris.sdk.next.api.IdentifierApplicationService;
import com.dris.sdk.next.application.support.BatchResultBuilder;
import com.dris.sdk.next.rule.IdentifierRules;
import com.dris.sdk.next.application.support.RegistrationRequestValidator;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierCode;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.enumtype.MetadataStatus;
import com.dris.sdk.next.enumtype.ObjectStatus;
import com.dris.sdk.next.exception.ConflictException;
import com.dris.sdk.next.exception.ValidationException;
import com.dris.sdk.next.infrastructure.IdentifierCredentialGenerator;
import com.dris.sdk.next.infrastructure.TimeProvider;
import com.dris.sdk.next.infrastructure.TransactionRunner;
import com.dris.sdk.next.repository.DigitalObjectRepository;
import com.dris.sdk.next.repository.RegistrationDictionary;
import com.dris.sdk.next.request.DeleteIdentifierRequest;
import com.dris.sdk.next.request.RegisterIdentifierRequest;
import com.dris.sdk.next.result.BatchResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultIdentifierApplicationService extends AbstractDigitalObjectService implements IdentifierApplicationService {
    private static final int CREDENTIAL_SEED_LENGTH = 9;
    private static final int MAX_ALLOCATION_ATTEMPTS = 64;

    private final RegistrationDictionary registrationDictionary;
    private final IdentifierCredentialGenerator credentialGenerator;
    private final RegistrationRequestValidator requestValidator;
    private final ConcurrentHashMap<String, String> idempotentCache = new ConcurrentHashMap<String, String>();

    public DefaultIdentifierApplicationService(DigitalObjectRepository repository,
                                               RegistrationDictionary registrationDictionary,
                                               TransactionRunner transactionRunner,
                                               TimeProvider timeProvider,
                                               IdentifierCredentialGenerator credentialGenerator) {
        this(repository, registrationDictionary, transactionRunner, timeProvider, credentialGenerator, new RegistrationRequestValidator());
    }

    public DefaultIdentifierApplicationService(DigitalObjectRepository repository,
                                               RegistrationDictionary registrationDictionary,
                                               TransactionRunner transactionRunner,
                                               TimeProvider timeProvider,
                                               IdentifierCredentialGenerator credentialGenerator,
                                               RegistrationRequestValidator requestValidator) {
        super(repository, transactionRunner, timeProvider);
        this.registrationDictionary = registrationDictionary;
        this.credentialGenerator = credentialGenerator;
        this.requestValidator = requestValidator;
    }

    @Override
    public String register(final RegisterIdentifierRequest request) {
        return transactionRunner.inTransaction(new TransactionRunner.TransactionalWork<String>() {
            @Override
            public String execute() {
                return doRegister(request);
            }
        });
    }

    @Override
    public BatchResult<String> registerBatch(List<RegisterIdentifierRequest> requests) {
        BatchResultBuilder<String> builder = new BatchResultBuilder<String>();
        if (requests == null) {
            return builder.build();
        }
        for (int index = 0; index < requests.size(); index++) {
            RegisterIdentifierRequest request = requests.get(index);
            String key = "index:" + index;
            try {
                builder.addSuccess(key, register(request));
            } catch (RuntimeException exception) {
                builder.addFailure(key, exception);
            }
        }
        return builder.build();
    }

    @Override
    public boolean validate(String identifier) {
        return IdentifierRules.isValid(identifier, registrationDictionary);
    }

    @Override
    public boolean exists(String identifier) {
        IdentifierRules.validateIdentifier(identifier);
        return repository.findByIdentifier(identifier)
                .map(object -> object.getStatus() != ObjectStatus.DELETED)
                .orElse(Boolean.FALSE);
    }

    @Override
    public IdentifierCode parse(String identifier) {
        return IdentifierRules.parse(identifier);
    }

    @Override
    public DigitalObject getDigitalObject(String identifier) {
        return loadActiveObject(identifier).copy();
    }

    @Override
    public BatchResult<DigitalObject> getDigitalObjects(List<String> identifiers) {
        BatchResultBuilder<DigitalObject> builder = new BatchResultBuilder<DigitalObject>();
        if (identifiers == null) {
            return builder.build();
        }
        for (String identifier : identifiers) {
            try {
                builder.addSuccess(identifier, getDigitalObject(identifier));
            } catch (RuntimeException exception) {
                builder.addFailure(identifier, exception);
            }
        }
        return builder.build();
    }

    @Override
    public void delete(final DeleteIdentifierRequest request) {
        transactionRunner.inTransaction(new TransactionRunner.TransactionalWork<Void>() {
            @Override
            public Void execute() {
                doDelete(request);
                return null;
            }
        });
    }

    private String doRegister(RegisterIdentifierRequest request) {
        requestValidator.validate(request, registrationDictionary);
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            String existing = idempotentCache.get(idempotencyKey);
            if (existing != null) {
                return existing;
            }
        }
        Instant now = now();
        String prefix = IdentifierRules.buildPrefix(request, registrationDictionary, now);
        String identifier = allocateIdentifier(request, now, prefix);
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            idempotentCache.putIfAbsent(idempotencyKey, identifier);
        }
        return identifier;
    }

    private String allocateIdentifier(RegisterIdentifierRequest request, Instant now, String prefix) {
        for (int i = 0; i < MAX_ALLOCATION_ATTEMPTS; i++) {
            String seed = credentialGenerator.nextCredential(CREDENTIAL_SEED_LENGTH);
            String identifier = IdentifierRules.composeIdentifier(prefix, seed);
            if (repository.existsByIdentifier(identifier)) {
                continue;
            }
            DigitalObject object = buildDigitalObject(identifier, request, now);
            try {
                repository.insert(object);
                return identifier;
            } catch (ConflictException exception) {
            }
        }
        throw new ConflictException("failed to allocate a unique identifier");
    }

    private DigitalObject buildDigitalObject(String identifier, RegisterIdentifierRequest request, Instant now) {
        String nowText = now.toString();
        IdentifierCode code = IdentifierRules.parse(identifier);

        IdentifierMetadata metadata = new IdentifierMetadata();
        metadata.setIdentifier(identifier);
        metadata.setRegionCode(request.getRegionCode());
        metadata.setOrganizationCode(request.getOrganizationCode());
        metadata.setIndustry(request.getIndustry());
        metadata.setDataCategory(request.getDataCategory());
        metadata.setRegistrationType(request.getRegistrationType());
        metadata.setDate(code.getDate());
        metadata.setOwner(request.getOwner());
        metadata.setDescription(request.getDescription());
        metadata.setApplicationScenario(request.getApplicationScenario());
        metadata.setName(request.getName());
        metadata.setEntityType(request.getEntityType());
        metadata.setStatus(MetadataStatus.ACTIVE.wireValue());
        metadata.setCreateTime(nowText);
        metadata.setUpdateTime(nowText);
        metadata.setAdditionalAttributes(request.getAdditionalAttributes());

        DigitalObject object = new DigitalObject();
        object.setIdentifier(identifier);
        object.setMetadata(metadata);
        object.setStatus(ObjectStatus.ACTIVE);
        object.setRevision(1L);
        object.validateInvariants();
        return object;
    }

    private void doDelete(DeleteIdentifierRequest request) {
        if (request == null) {
            throw new ValidationException("delete request is required");
        }
        DigitalObject object = loadActiveObject(request.getIdentifier());
        long expectedRevision = expectedRevisionOrCurrent(request.getExpectedRevision(), object);
        object.setStatus(ObjectStatus.DELETED);
        if (object.getMetadata() != null) {
            object.getMetadata().setStatus(MetadataStatus.DELETED.wireValue());
            object.getMetadata().setUpdateTime(nowText());
        }
        object.setRevision(object.getRevision() + 1L);
        saveUpdatedObject(object, expectedRevision);
    }
}
