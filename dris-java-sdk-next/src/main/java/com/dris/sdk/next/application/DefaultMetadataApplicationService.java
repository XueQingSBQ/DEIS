package com.dris.sdk.next.application;

import com.dris.sdk.next.api.MetadataApplicationService;
import com.dris.sdk.next.application.support.BatchResultBuilder;
import com.dris.sdk.next.rule.IdentifierRules;
import com.dris.sdk.next.application.support.MetadataPatchApplier;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierCode;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.exception.ValidationException;
import com.dris.sdk.next.infrastructure.TimeProvider;
import com.dris.sdk.next.infrastructure.TransactionRunner;
import com.dris.sdk.next.repository.DigitalObjectRepository;
import com.dris.sdk.next.repository.RegistrationDictionary;
import com.dris.sdk.next.request.PatchMetadataRequest;
import com.dris.sdk.next.request.UpdateMetadataRequest;
import com.dris.sdk.next.result.BatchResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultMetadataApplicationService extends AbstractDigitalObjectService implements MetadataApplicationService {
    private final RegistrationDictionary registrationDictionary;
    private final MetadataPatchApplier patchApplier;

    public DefaultMetadataApplicationService(DigitalObjectRepository repository,
                                             RegistrationDictionary registrationDictionary,
                                             TransactionRunner transactionRunner,
                                             TimeProvider timeProvider) {
        this(repository, registrationDictionary, transactionRunner, timeProvider, new MetadataPatchApplier());
    }

    public DefaultMetadataApplicationService(DigitalObjectRepository repository,
                                             RegistrationDictionary registrationDictionary,
                                             TransactionRunner transactionRunner,
                                             TimeProvider timeProvider,
                                             MetadataPatchApplier patchApplier) {
        super(repository, transactionRunner, timeProvider);
        this.registrationDictionary = registrationDictionary;
        this.patchApplier = patchApplier;
    }

    @Override
    public IdentifierMetadata get(String identifier) {
        return requiredMetadata(loadActiveObject(identifier)).copy();
    }

    @Override
    public BatchResult<IdentifierMetadata> getBatch(List<String> identifiers) {
        BatchResultBuilder<IdentifierMetadata> builder = new BatchResultBuilder<IdentifierMetadata>();
        if (identifiers == null) {
            return builder.build();
        }
        for (String identifier : identifiers) {
            try {
                builder.addSuccess(identifier, get(identifier));
            } catch (RuntimeException exception) {
                builder.addFailure(identifier, exception);
            }
        }
        return builder.build();
    }

    @Override
    public IdentifierMetadata update(final UpdateMetadataRequest request) {
        return transactionRunner.inTransaction(new TransactionRunner.TransactionalWork<IdentifierMetadata>() {
            @Override
            public IdentifierMetadata execute() {
                return doUpdate(request);
            }
        });
    }

    @Override
    public IdentifierMetadata patch(final PatchMetadataRequest request) {
        return transactionRunner.inTransaction(new TransactionRunner.TransactionalWork<IdentifierMetadata>() {
            @Override
            public IdentifierMetadata execute() {
                return doPatch(request);
            }
        });
    }

    private IdentifierMetadata doUpdate(UpdateMetadataRequest request) {
        if (request == null || request.getMetadata() == null) {
            throw new ValidationException("update metadata request is required");
        }
        DigitalObject object = loadActiveObject(request.getIdentifier());
        long expectedRevision = expectedRevisionOrCurrent(request.getExpectedRevision(), object);
        IdentifierMetadata current = requiredMetadata(object);
        IdentifierMetadata updated = request.getMetadata().copy();
        prepareMetadataForSave(object.getIdentifier(), current, updated);
        validateMetadata(updated);
        object.setMetadata(updated);
        object.setRevision(object.getRevision() + 1L);
        saveUpdatedObject(object, expectedRevision);
        return updated.copy();
    }

    private IdentifierMetadata doPatch(PatchMetadataRequest request) {
        if (request == null) {
            throw new ValidationException("patch metadata request is required");
        }
        DigitalObject object = loadActiveObject(request.getIdentifier());
        long expectedRevision = expectedRevisionOrCurrent(request.getExpectedRevision(), object);
        IdentifierMetadata current = requiredMetadata(object);
        IdentifierMetadata patched = patchApplier.apply(current, request.getChanges());
        prepareMetadataForSave(object.getIdentifier(), current, patched);
        validateMetadata(patched);
        object.setMetadata(patched);
        object.setRevision(object.getRevision() + 1L);
        saveUpdatedObject(object, expectedRevision);
        return patched.copy();
    }

    private void prepareMetadataForSave(String identifier, IdentifierMetadata current, IdentifierMetadata target) {
        IdentifierCode code = IdentifierRules.parse(identifier);
        target.setIdentifier(identifier);
        target.setRegionCode(code.getRegionCode());
        target.setOrganizationCode(code.getOrganizationCode());
        target.setDate(code.getDate());
        target.setCreateTime(firstNonBlank(target.getCreateTime(), current == null ? null : current.getCreateTime(), nowText()));
        target.setUpdateTime(nowText());
        String fallbackStatus = current == null ? null : current.getStatus();
        target.setStatus(IdentifierRules.normalizeStatus(target.getStatus(), fallbackStatus));
        Map<String, Object> attrs = target.getAdditionalAttributes() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(target.getAdditionalAttributes());
        target.setAdditionalAttributes(attrs);
    }

    private IdentifierMetadata requiredMetadata(DigitalObject object) {
        if (object.getMetadata() == null) {
            throw new ValidationException("metadata not found for identifier: " + object.getIdentifier());
        }
        return object.getMetadata();
    }

    private void validateMetadata(IdentifierMetadata metadata) {
        if (metadata == null) {
            throw new ValidationException("metadata is required");
        }
        metadata.validateForUpdate();
        IdentifierCode code = IdentifierRules.parse(metadata.getIdentifier());
        if (!code.getRegionCode().equals(metadata.getRegionCode())) {
            throw new ValidationException("regionCode does not match identifier rule");
        }
        if (!code.getOrganizationCode().equals(metadata.getOrganizationCode())) {
            throw new ValidationException("organizationCode does not match identifier rule");
        }
        if (!code.getDate().equals(metadata.getDate())) {
            throw new ValidationException("date does not match identifier rule");
        }
        requireText(metadata.getIndustry(), "industry is required");
        IdentifierRules.resolveDataCategoryCode(metadata.getDataCategory());
        IdentifierRules.resolveRegistrationTypeCode(metadata.getRegistrationType());
        requireText(metadata.getOwner(), "owner is required");
        if (!registrationDictionary.existsRegionCode(metadata.getRegionCode())) {
            throw new ValidationException("regionCode does not exist");
        }
        if (!registrationDictionary.existsOrganizationCode(metadata.getOrganizationCode())) {
            throw new ValidationException("organizationCode does not exist");
        }
        if (!registrationDictionary.existsIndustry(metadata.getIndustry())) {
            throw new ValidationException("industry does not exist");
        }
        if (!IdentifierRules.isAllowedStatus(metadata.getStatus())) {
            throw new ValidationException("status is invalid");
        }
        if (metadata.getOwner() != null && metadata.getOwner().length() > 100) {
            throw new ValidationException("owner is too long");
        }
        if (metadata.getDescription() != null && metadata.getDescription().length() > 500) {
            throw new ValidationException("description is too long");
        }
        if (metadata.getApplicationScenario() != null && metadata.getApplicationScenario().length() > 200) {
            throw new ValidationException("applicationScenario is too long");
        }
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return fallback;
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(message);
        }
    }
}
