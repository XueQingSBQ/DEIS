package com.dris.v2.backend.service;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.dto.BatchResolveResourceResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.sdknext.BackendKernelResponseSupport;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IdentifierResourceService {
    private final LocalDrisKernelFacade localDrisKernelFacade;
    private final DrirpResolutionService drirpResolutionService;
    private final IdentifierPermissionService identifierPermissionService;
    private final IdentifierBusinessAuditService identifierBusinessAuditService;

    public IdentifierResourceService(
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

    public DrisResponse<Resource> getComponent(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        return drirpResolutionService.resolveResourceDistributed(identifier.trim());
    }

    public DrisResponse<BatchResolveResourceResponse> resolveResourceBatchDistributed(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifiers is required", HttpStatus.BAD_REQUEST);
        }
        return drirpResolutionService.resolveResourceBatchDistributed(identifiers);
    }

    public DrisResponse<Boolean> upsertComponent(String identifier, Resource resource, boolean forceReplaceType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        if (resource == null) {
            throw new BusinessException("VALIDATION_ERROR", "resource is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        try {
            identifierPermissionService.assertCurrentUserCanWriteIdentifier(trimmed);
            DrisResponse<Boolean> response = BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.upsertResource(trimmed, resource, forceReplaceType)));
            recordAudit(trimmed, IdentifierAuditOperations.UPSERT_COMPONENT, response);
            return response;
        } catch (RuntimeException ex) {
            recordFailure(trimmed, IdentifierAuditOperations.UPSERT_COMPONENT, ex);
            return asFailure(ex);
        }
    }

    public DrisResponse<Boolean> removeComponent(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        try {
            identifierPermissionService.assertCurrentUserCanWriteIdentifier(trimmed);
            DrisResponse<Boolean> response = BackendKernelResponseSupport.success(Boolean.valueOf(localDrisKernelFacade.removeResource(trimmed)));
            recordAudit(trimmed, IdentifierAuditOperations.REMOVE_COMPONENT, response);
            return response;
        } catch (RuntimeException ex) {
            recordFailure(trimmed, IdentifierAuditOperations.REMOVE_COMPONENT, ex);
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
}
