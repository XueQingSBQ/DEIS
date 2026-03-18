package com.dris.v2.backend.service;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.service.local.upload.LocalUploadService;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.upload.CreateUploadSessionRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.upload.UploadCreateSessionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierUploadServiceTest {
    @Mock
    private LocalUploadService localUploadService;
    @Mock
    private DrirpOperationService drirpOperationService;
    @Mock
    private IdentifierPermissionService identifierPermissionService;
    @Mock
    private IdentifierBusinessAuditService identifierBusinessAuditService;
    @InjectMocks
    private IdentifierUploadService service;

    @Test
    void createUploadSession_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.createUploadSession(" ", new CreateUploadSessionRequest()));
    }

    @Test
    void createUploadSession_nullRequest_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.createUploadSession(TestSamples.VALID_IDENTIFIER, null));
    }

    @Test
    void completeUpload_blankUploadId_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.completeUpload(""));
    }

    @Test
    void createDownloadPresign_nullRequest_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.createDownloadPresign(null));
    }

    @Test
    void createDownloadPresign_blankIdentifier_shouldThrow() {
        DownloadPresignRequest request = new DownloadPresignRequest();
        request.setIdentifier(" ");
        assertThrows(BusinessException.class, () -> service.createDownloadPresign(request));
    }

    @Test
    void createUploadSession_valid_shouldDelegate() {
        CreateUploadSessionRequest request = TestSamples.uploadSessionRequest();
        when(localUploadService.createUploadSession(TestSamples.VALID_IDENTIFIER, request)).thenReturn(DrisResponse.success(new UploadCreateSessionResult()));
        DrisResponse<UploadCreateSessionResult> out = service.createUploadSession(TestSamples.VALID_IDENTIFIER, request);
        assertTrue(out.isOk());
        verify(identifierPermissionService).assertCurrentUserCanWriteIdentifier(TestSamples.VALID_IDENTIFIER);
        verify(localUploadService).createUploadSession(TestSamples.VALID_IDENTIFIER, request);
    }

    @Test
    void completeUpload_valid_shouldDelegate() {
        when(localUploadService.completeUpload("up-1")).thenReturn(DrisResponse.success(new Resource()));
        DrisResponse<Resource> out = service.completeUpload("up-1");
        assertTrue(out.isOk());
        verify(identifierPermissionService).assertCurrentUserCanCompleteUpload("up-1");
        verify(localUploadService).completeUpload("up-1");
    }

    @Test
    void createDownloadPresign_valid_shouldDelegate() {
        DownloadPresignRequest request = TestSamples.downloadPresignRequest();
        when(drirpOperationService.createDownloadPresignDistributed(request)).thenReturn(DrisResponse.success(new DownloadPresignResult()));
        DrisResponse<DownloadPresignResult> out = service.createDownloadPresign(request);
        assertTrue(out.isOk());
        verify(drirpOperationService).createDownloadPresignDistributed(request);
    }
}
