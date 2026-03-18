package com.dris.v2.backend.sdknext;

import com.dris.v2.backend.contract.registration.RegistrationRequest;
import com.dris.sdk.next.api.DrisSdk;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.sdk.next.exception.ValidationException;
import com.dris.sdk.next.request.BindResourceRequest;
import com.dris.sdk.next.request.DeleteIdentifierRequest;
import com.dris.sdk.next.request.PatchMetadataRequest;
import com.dris.sdk.next.request.RegisterIdentifierRequest;
import com.dris.sdk.next.request.RemoveResourceRequest;
import com.dris.sdk.next.request.UpdateMetadataRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service("delegateLocalDrisKernelFacade")
public class DefaultLocalDrisKernelFacade implements LocalDrisKernelFacade {
    private final DrisSdk drisSdk;
    private final LocalIdentifierPolicyValidator identifierPolicyValidator;
    private final LocalResourcePolicyValidator resourcePolicyValidator;
    private final ResourceVersionWriteDao resourceVersionWriteDao;

    public DefaultLocalDrisKernelFacade(
            DrisSdk drisSdk,
            LocalIdentifierPolicyValidator identifierPolicyValidator,
            LocalResourcePolicyValidator resourcePolicyValidator,
            ResourceVersionWriteDao resourceVersionWriteDao
    ) {
        this.drisSdk = drisSdk;
        this.identifierPolicyValidator = identifierPolicyValidator;
        this.resourcePolicyValidator = resourcePolicyValidator;
        this.resourceVersionWriteDao = resourceVersionWriteDao;
    }

    @Override
    public String register(RegistrationRequest request) {
        if (request == null) {
            throw new ValidationException("request is required");
        }
        identifierPolicyValidator.validateRegisterTarget(request.getRegionCode(), request.getOrganizationCode());
        return drisSdk.identifiers().register(toNextRequest(request));
    }

    @Override
    public List<String> registerBatch(List<RegistrationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ValidationException("requests is required");
        }
        List<String> out = new ArrayList<String>(requests.size());
        for (RegistrationRequest request : requests) {
            try {
                out.add(register(request));
            } catch (RuntimeException exception) {
                out.add(null);
            }
        }
        return out;
    }

    @Override
    public boolean validateIdentifier(String identifier) {
        return identifierPolicyValidator.isValidForLocalNode(identifier);
    }

    @Override
    public boolean existsIdentifier(String identifier) {
        if (!validateIdentifier(identifier)) {
            return false;
        }
        return drisSdk.identifiers().exists(identifier.trim());
    }

    @Override
    public IdentifierMetadata getMetadata(String identifier) {
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        return drisSdk.metadata().get(identifier.trim());
    }

    @Override
    public List<IdentifierMetadata> getMetadataBatch(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            throw new ValidationException("identifiers is required");
        }
        List<IdentifierMetadata> out = new ArrayList<IdentifierMetadata>(identifiers.size());
        for (String identifier : identifiers) {
            try {
                out.add(getMetadata(identifier));
            } catch (RuntimeException exception) {
                out.add(null);
            }
        }
        return out;
    }

    @Override
    public DigitalObject getDigitalObject(String identifier) {
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        return drisSdk.identifiers().getDigitalObject(identifier.trim());
    }

    @Override
    public boolean updateMetadata(String identifier, IdentifierMetadata metadata) {
        if (metadata == null) {
            throw new ValidationException("metadata is required");
        }
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        UpdateMetadataRequest request = new UpdateMetadataRequest();
        request.setIdentifier(identifier.trim());
        request.setExpectedRevision(readExpectedRevision(metadata.getAdditionalAttributes()));
        request.setMetadata(metadata);
        drisSdk.metadata().update(request);
        return true;
    }

    @Override
    public boolean patchMetadata(String identifier, Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new ValidationException("fields is required");
        }
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        PatchMetadataRequest request = new PatchMetadataRequest();
        request.setIdentifier(identifier.trim());
        request.setChanges(new LinkedHashMap<String, Object>(fields));
        Object expectedRevision = fields.get("_expectedRevision");
        if (expectedRevision != null) {
            try {
                request.setExpectedRevision(Long.valueOf(String.valueOf(expectedRevision)));
            } catch (Exception ignore) {
            }
        }
        drisSdk.metadata().patch(request);
        return true;
    }

    @Override
    public boolean deleteIdentifier(String identifier) {
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        DeleteIdentifierRequest request = new DeleteIdentifierRequest();
        request.setIdentifier(identifier.trim());
        drisSdk.identifiers().delete(request);
        return true;
    }

    @Override
    public Resource getResource(String identifier) {
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        return drisSdk.resources().get(identifier.trim());
    }

    @Override
    @Transactional
    public boolean upsertResource(String identifier, Resource resource, boolean forceReplaceType) {
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        Resource existing = null;
        try {
            existing = drisSdk.resources().get(identifier.trim());
        } catch (RuntimeException ignore) {
        }
        resourcePolicyValidator.validateForBind(existing, resource, forceReplaceType);
        BindResourceRequest request = new BindResourceRequest();
        request.setIdentifier(identifier.trim());
        request.setExpectedRevision(readExpectedRevision(resource == null ? null : resource.getExtra()));
        request.setResource(resource);
        Resource saved = drisSdk.resources().bind(request);
        resourceVersionWriteDao.insert(identifier.trim(), saved);
        return true;
    }

    @Override
    public boolean removeResource(String identifier) {
        identifierPolicyValidator.parseAndValidateLocalIdentifier(identifier);
        RemoveResourceRequest request = new RemoveResourceRequest();
        request.setIdentifier(identifier.trim());
        drisSdk.resources().remove(request);
        return true;
    }

    private RegisterIdentifierRequest toNextRequest(RegistrationRequest source) {
        RegisterIdentifierRequest target = new RegisterIdentifierRequest();
        target.setRegionCode(source.getRegionCode());
        target.setOrganizationCode(source.getOrganizationCode());
        target.setIndustry(source.getIndustry());
        target.setDataCategory(source.getDataCategory());
        target.setRegistrationType(source.getRegistrationType());
        target.setOwner(source.getOwner());
        target.setDescription(source.getDescription());
        target.setApplicationScenario(source.getApplicationScenario());
        target.setName(source.getName());
        target.setEntityType(source.getEntityType());
        target.setAdditionalAttributes(source.getAdditionalAttributes());
        target.setIdempotencyKey(resolveIdempotencyKey(source));
        return target;
    }

    private String resolveIdempotencyKey(RegistrationRequest source) {
        if (source == null || source.getAdditionalAttributes() == null) {
            return null;
        }
        Object raw = source.getAdditionalAttributes().get("_idempotencyKey");
        if (raw == null) {
            raw = source.getAdditionalAttributes().get("idempotencyKey");
        }
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }
    private Long readExpectedRevision(Map<String, Object> values) {
        if (values == null || !values.containsKey("_expectedRevision")) {
            return null;
        }
        Object raw = values.get("_expectedRevision");
        if (raw instanceof Number) {
            return Long.valueOf(((Number) raw).longValue());
        }
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (Exception exception) {
            return null;
        }
    }
}
