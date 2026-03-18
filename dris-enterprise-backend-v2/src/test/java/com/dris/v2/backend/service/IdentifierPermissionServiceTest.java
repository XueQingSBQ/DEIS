package com.dris.v2.backend.service;

import com.dris.v2.backend.auth.AuthProperties;
import com.dris.v2.backend.auth.CurrentUser;
import com.dris.v2.backend.auth.CurrentUserContext;
import com.dris.v2.backend.dao.IdentifierPermissionDao;
import com.dris.v2.backend.model.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierPermissionServiceTest {
    @Mock
    private IdentifierPermissionDao identifierPermissionDao;

    private AuthProperties authProperties;
    private IdentifierPermissionService service;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        service = new IdentifierPermissionService(identifierPermissionDao, authProperties);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void bindCreatorAfterRegister_withCurrentUser_shouldUpdateRow() {
        CurrentUserContext.set(new CurrentUser("u_1", "alice", "Alice", "ABCD", "jti-1"));
        when(identifierPermissionDao.assignCreatorUserId("id-1", "u_1")).thenReturn(1);

        service.bindCreatorAfterRegister("id-1");

        verify(identifierPermissionDao).assignCreatorUserId("id-1", "u_1");
    }

    @Test
    void assertCurrentUserCanWriteIdentifier_nonCreatorShouldThrow() {
        authProperties.setWritePermissionEnabled(true);
        CurrentUserContext.set(new CurrentUser("u_1", "alice", "Alice", "ABCD", "jti-1"));
        when(identifierPermissionDao.findCreatorUserId("id-1")).thenReturn("u_2");

        assertThrows(BusinessException.class, () -> service.assertCurrentUserCanWriteIdentifier("id-1"));
    }

    @Test
    void assertCurrentUserCanCompleteUpload_missingUploadShouldThrow() {
        authProperties.setWritePermissionEnabled(true);
        CurrentUserContext.set(new CurrentUser("u_1", "alice", "Alice", "ABCD", "jti-1"));
        when(identifierPermissionDao.findIdentifierByUploadId("up-1")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.assertCurrentUserCanCompleteUpload("up-1"));
    }
}
