package com.dris.v2.backend.service;

import com.dris.v2.backend.contract.registration.RegistrationRequest;
import com.dris.v2.backend.contract.registration.IdentifierRegistrationView;
import com.dris.v2.backend.contract.registration.IdentifierStatusUpdateRequest;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectResponse;
import com.dris.v2.backend.dto.BatchResolveMetadataResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.sdknext.BackendKernelResponseSupport;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IdentifierCoreService {
    private final LocalDrisKernelFacade localDrisKernelFacade;
    private final DrirpResolutionService drirpResolutionService;
    private final IdentifierPermissionService identifierPermissionService;
    private final IdentifierBusinessAuditService identifierBusinessAuditService;

    public IdentifierCoreService(
            LocalDrisKernelFacade localDrisKernelFacade,
            DrirpResolutionService drirpResolutionService,
            IdentifierPermissionService identifierPermissionService,
            IdentifierBusinessAuditService identifierBusinessAuditService
    ) {
        this.localDrisKernelFacade = localDrisKernelFacade;
        this.drirpResolutionService = drirpResolutionService;
        this.identifierPermissionService = identifierPermissionService;
        this.identifierBusinessAuditService = identifierBusinessAuditService;
    }

    public DrisResponse<String> register(RegistrationRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        String identifier = "";
        try {
            identifier = safe(localDrisKernelFacade.register(request));
            DrisResponse<String> response = BackendKernelResponseSupport.success(identifier);
            if (!identifier.isEmpty()) {
                identifierPermissionService.bindCreatorAfterRegister(identifier);
                recordAudit(identifier, IdentifierAuditOperations.REGISTER, response);
            }
            return response;
        } catch (RuntimeException ex) {
            if (!identifier.isEmpty()) {
                recordFailure(identifier, IdentifierAuditOperations.REGISTER, ex);
            }
            return asFailure(ex);
        }
    }

    public DrisResponse<IdentifierRegistrationView> registerDetail(RegistrationRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        String identifier = "";
        try {
            identifier = safe(localDrisKernelFacade.register(request));
            IdentifierMetadata metadata = null;
            if (!identifier.isEmpty()) {
                identifierPermissionService.bindCreatorAfterRegister(identifier);
                metadata = readMetadataQuietly(identifier);
            }
            DrisResponse<IdentifierRegistrationView> response = BackendKernelResponseSupport.success(
                    IdentifierRegistrationView.from(identifier, metadata)
            );
            if (!identifier.isEmpty()) {
                recordAudit(identifier, IdentifierAuditOperations.REGISTER, response);
            }
            return response;
        } catch (RuntimeException ex) {
            if (!identifier.isEmpty()) {
                recordFailure(identifier, IdentifierAuditOperations.REGISTER, ex);
            }
            return asFailure(ex);
        }
    }

    public DrisResponse<IdentifierMetadata> resolveMetadata(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        return drirpResolutionService.resolveMetadataDistributed(identifier.trim());
    }

    public DrisResponse<List<String>> registerBatch(List<RegistrationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "requests is required", HttpStatus.BAD_REQUEST);
        }
        List<String> identifiers = null;
        try {
            identifiers = localDrisKernelFacade.registerBatch(requests);
            DrisResponse<List<String>> response = BackendKernelResponseSupport.success(identifiers);
            identifierPermissionService.bindCreatorAfterRegisterBatch(identifiers);
            if (identifiers != null) {
                for (String identifier : identifiers) {
                    if (!isBlank(identifier)) {
                        recordAudit(identifier.trim(), IdentifierAuditOperations.REGISTER_BATCH, response);
                    }
                }
            }
            return response;
        } catch (RuntimeException ex) {
            if (identifiers != null) {
                for (String identifier : identifiers) {
                    if (!isBlank(identifier)) {
                        recordFailure(identifier.trim(), IdentifierAuditOperations.REGISTER_BATCH, ex);
                    }
                }
            }
            return asFailure(ex);
        }
    }

    public DrisResponse<Boolean> validateIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.validateIdentifier(identifier.trim())));
        } catch (RuntimeException ex) {
            return asFailure(ex);
        }
    }

    public DrisResponse<Boolean> existsIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.existsIdentifier(identifier.trim())));
        } catch (RuntimeException ex) {
            return asFailure(ex);
        }
    }

    public DrisResponse<List<IdentifierMetadata>> resolveMetadataBatch(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifiers is required", HttpStatus.BAD_REQUEST);
        }
        try {
            return BackendKernelResponseSupport.success(localDrisKernelFacade.getMetadataBatch(identifiers));
        } catch (RuntimeException ex) {
            return asFailure(ex);
        }
    }

    public DrisResponse<BatchResolveMetadataResponse> resolveMetadataBatchDistributed(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifiers is required", HttpStatus.BAD_REQUEST);
        }
        return drirpResolutionService.resolveMetadataBatchDistributed(identifiers);
    }

    public DrisResponse<BatchResolveDigitalObjectResponse> resolveDigitalObjectBatchDistributed(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifiers is required", HttpStatus.BAD_REQUEST);
        }
        return drirpResolutionService.resolveDigitalObjectBatchDistributed(identifiers);
    }

    public DrisResponse<DigitalObject> getDigitalObject(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        return drirpResolutionService.resolveDigitalObjectDistributed(identifier.trim());
    }

    public DrisResponse<Boolean> updateIdentifier(String identifier, IdentifierMetadata metadata) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        if (metadata == null) {
            throw new BusinessException("VALIDATION_ERROR", "metadata is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        try {
            identifierPermissionService.assertCurrentUserCanWriteIdentifier(trimmed);
            if (metadata.getIdentifier() == null || metadata.getIdentifier().trim().isEmpty()) {
                metadata.setIdentifier(trimmed);
            } else if (!trimmed.equals(metadata.getIdentifier().trim())) {
                throw new BusinessException("VALIDATION_ERROR", "path identifier must equal metadata.identifier", HttpStatus.BAD_REQUEST);
            }
            DrisResponse<Boolean> response = BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.updateMetadata(trimmed, metadata)));
            recordAudit(trimmed, IdentifierAuditOperations.UPDATE_METADATA, response);
            return response;
        } catch (RuntimeException ex) {
            recordFailure(trimmed, IdentifierAuditOperations.UPDATE_METADATA, ex);
            return asFailure(ex);
        }
    }

    public DrisResponse<Boolean> partialUpdateIdentifier(String identifier, Map<String, String> fields) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        if (fields == null || fields.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "fields is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        try {
            identifierPermissionService.assertCurrentUserCanWriteIdentifier(trimmed);
            DrisResponse<Boolean> response = BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.patchMetadata(trimmed, fields)));
            recordAudit(trimmed, IdentifierAuditOperations.PARTIAL_UPDATE_METADATA, response);
            return response;
        } catch (RuntimeException ex) {
            recordFailure(trimmed, IdentifierAuditOperations.PARTIAL_UPDATE_METADATA, ex);
            return asFailure(ex);
        }
    }

    public DrisResponse<Boolean> updateIdentifierStatus(String identifier, IdentifierStatusUpdateRequest request) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        if (request == null || isBlank(request.getStatus())) {
            throw new BusinessException("VALIDATION_ERROR", "status is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        String normalizedStatus = request.getStatus().trim().toLowerCase(Locale.ROOT);
        if (!"active".equals(normalizedStatus) && !"inactive".equals(normalizedStatus)) {
            throw new BusinessException("VALIDATION_ERROR", "status must be active or inactive", HttpStatus.BAD_REQUEST);
        }
        try {
            identifierPermissionService.assertCurrentUserCanWriteIdentifier(trimmed);
            Map<String, String> fields = new LinkedHashMap<String, String>();
            fields.put("status", normalizedStatus);
            DrisResponse<Boolean> response = BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.patchMetadata(trimmed, fields)));
            recordAudit(trimmed, IdentifierAuditOperations.UPDATE_STATUS, response);
            return response;
        } catch (RuntimeException ex) {
            recordFailure(trimmed, IdentifierAuditOperations.UPDATE_STATUS, ex);
            return asFailure(ex);
        }
    }

    public DrisResponse<Boolean> deleteIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        try {
            identifierPermissionService.assertCurrentUserCanWriteIdentifier(trimmed);
            DrisResponse<Boolean> response = BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.deleteIdentifier(trimmed)));
            recordAudit(trimmed, IdentifierAuditOperations.DELETE_IDENTIFIER, response);
            return response;
        } catch (RuntimeException ex) {
            recordFailure(trimmed, IdentifierAuditOperations.DELETE_IDENTIFIER, ex);
            return asFailure(ex);
        }
    }

    private void recordAudit(String identifier, String operation, DrisResponse<?> response) {
        boolean success = response != null && response.isOk();
        String message = success
                ? "ok"
                : "code=" + safe(response == null ? null : response.getCode()) + ", message=" + safe(response == null ? null : response.getMessage());
        identifierBusinessAuditService.record(identifier, operation, success, message);
    }

    private void recordFailure(String identifier, String operation, RuntimeException ex) {
        identifierBusinessAuditService.record(identifier, operation, false, ex == null ? "" : safe(ex.getMessage()));
    }

    private <T> DrisResponse<T> asFailure(RuntimeException ex) {
        if (ex instanceof BusinessException) {
            throw (BusinessException) ex;
        }
        return BackendKernelResponseSupport.failure(ex);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private IdentifierMetadata readMetadataQuietly(String identifier) {
        try {
            return localDrisKernelFacade.getMetadata(identifier);
        } catch (RuntimeException ignore) {
            return null;
        }
    }
}
