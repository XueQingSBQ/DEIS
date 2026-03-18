package com.dris.v2.backend.service;

import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.service.local.upload.LocalUploadService;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.upload.CreateUploadSessionRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.upload.UploadCreateSessionResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdentifierUploadService {
    private final LocalUploadService localUploadService;
    private final DrirpOperationService drirpOperationService;
    private final IdentifierPermissionService identifierPermissionService;
    private final IdentifierBusinessAuditService identifierBusinessAuditService;

    public IdentifierUploadService(
            LocalUploadService localUploadService,
            DrirpOperationService drirpOperationService,
            IdentifierPermissionService identifierPermissionService,
            IdentifierBusinessAuditService identifierBusinessAuditService
    ) {
        this.localUploadService = localUploadService;
        this.drirpOperationService = drirpOperationService;
        this.identifierPermissionService = identifierPermissionService;
        this.identifierBusinessAuditService = identifierBusinessAuditService;
    }

    public DrisResponse<UploadCreateSessionResult> createUploadSession(String identifier, CreateUploadSessionRequest request) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = identifier.trim();
        try {
            identifierPermissionService.assertCurrentUserCanWriteIdentifier(trimmed);
            DrisResponse<UploadCreateSessionResult> response = localUploadService.createUploadSession(trimmed, request);
            recordAudit(trimmed, IdentifierAuditOperations.CREATE_UPLOAD_SESSION, response);
            return response;
        } catch (RuntimeException ex) {
            recordFailure(trimmed, IdentifierAuditOperations.CREATE_UPLOAD_SESSION, ex);
            throw ex;
        }
    }

    public DrisResponse<Resource> completeUpload(String uploadId) {
        if (uploadId == null || uploadId.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "uploadId is required", HttpStatus.BAD_REQUEST);
        }
        String trimmed = uploadId.trim();
        String identifier = identifierPermissionService.findIdentifierByUploadId(trimmed);
        try {
            identifierPermissionService.assertCurrentUserCanCompleteUpload(trimmed);
            DrisResponse<Resource> response = localUploadService.completeUpload(trimmed);
            if (!isBlank(identifier)) {
                recordAudit(identifier, IdentifierAuditOperations.COMPLETE_UPLOAD, response);
            }
            return response;
        } catch (RuntimeException ex) {
            if (!isBlank(identifier)) {
                recordFailure(identifier, IdentifierAuditOperations.COMPLETE_UPLOAD, ex);
            }
            throw ex;
        }
    }

    public DrisResponse<DownloadPresignResult> createDownloadPresign(DownloadPresignRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getIdentifier() == null || request.getIdentifier().trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        return drirpOperationService.createDownloadPresignDistributed(request);
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

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}