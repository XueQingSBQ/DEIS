package com.dris.v2.backend.service;

import com.dris.v2.backend.auth.AuthProperties;
import com.dris.v2.backend.auth.CurrentUserContext;
import com.dris.v2.backend.dao.IdentifierPermissionDao;
import com.dris.v2.backend.model.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IdentifierPermissionService {
    private final IdentifierPermissionDao identifierPermissionDao;
    private final AuthProperties authProperties;

    public IdentifierPermissionService(IdentifierPermissionDao identifierPermissionDao, AuthProperties authProperties) {
        this.identifierPermissionDao = identifierPermissionDao;
        this.authProperties = authProperties;
    }

    public void bindCreatorAfterRegister(String identifier) {
        String currentUserId = CurrentUserContext.getUserIdOrNull();
        if (isBlank(currentUserId)) {
            if (authProperties.isWritePermissionEnabled()) {
                throw new BusinessException("UNAUTHORIZED", "authentication is required for register", HttpStatus.UNAUTHORIZED);
            }
            return;
        }
        int updated = identifierPermissionDao.assignCreatorUserId(identifier, currentUserId);
        if (updated <= 0) {
            throw new BusinessException("REGISTER_BIND_FAILED", "failed to bind creator_user_id", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void bindCreatorAfterRegisterBatch(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return;
        }
        for (String identifier : identifiers) {
            if (!isBlank(identifier)) {
                bindCreatorAfterRegister(identifier.trim());
            }
        }
    }

    public void assertCurrentUserCanWriteIdentifier(String identifier) {
        if (!authProperties.isWritePermissionEnabled()) {
            return;
        }
        String currentUserId = requireCurrentUserId();
        String creatorUserId = trim(identifierPermissionDao.findCreatorUserId(identifier));
        if (isBlank(creatorUserId)) {
            throw new BusinessException("FORBIDDEN", "creator_user_id is not bound for identifier", HttpStatus.FORBIDDEN);
        }
        if (!creatorUserId.equals(currentUserId)) {
            throw new BusinessException("FORBIDDEN", "only creator can modify identifier", HttpStatus.FORBIDDEN);
        }
    }

    public void assertCurrentUserCanCompleteUpload(String uploadId) {
        if (!authProperties.isWritePermissionEnabled()) {
            return;
        }
        String identifier = trim(identifierPermissionDao.findIdentifierByUploadId(uploadId));
        if (isBlank(identifier)) {
            throw new BusinessException("NOT_FOUND", "upload session not found", HttpStatus.NOT_FOUND);
        }
        assertCurrentUserCanWriteIdentifier(identifier);
    }

    public String findIdentifierByUploadId(String uploadId) {
        return trim(identifierPermissionDao.findIdentifierByUploadId(uploadId));
    }

    private String requireCurrentUserId() {
        String currentUserId = CurrentUserContext.getUserIdOrNull();
        if (isBlank(currentUserId)) {
            throw new BusinessException("UNAUTHORIZED", "authentication is required", HttpStatus.UNAUTHORIZED);
        }
        return currentUserId.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
