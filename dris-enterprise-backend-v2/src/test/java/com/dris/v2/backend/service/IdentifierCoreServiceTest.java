package com.dris.v2.backend.service;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectResponse;
import com.dris.v2.backend.dto.BatchResolveMetadataResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.v2.backend.contract.registration.RegistrationRequest;
import com.dris.v2.backend.contract.DrisResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierCoreServiceTest {
    @Mock
    private LocalDrisKernelFacade localDrisKernelFacade;
    @Mock
    private DrirpResolutionService drirpResolutionService;
    @Mock
    private IdentifierPermissionService identifierPermissionService;
    @Mock
    private IdentifierBusinessAuditService identifierBusinessAuditService;
    @InjectMocks
    private IdentifierCoreService service;

    @Test
    void register_nullRequest_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.register(null));
    }

    @Test
    void register_valid_shouldDelegateAndBindCreator() {
        RegistrationRequest request = TestSamples.registrationRequest();
        when(localDrisKernelFacade.register(request)).thenReturn(TestSamples.VALID_IDENTIFIER);

        DrisResponse<String> out = service.register(request);

        assertTrue(out.isOk());
        assertEquals(TestSamples.VALID_IDENTIFIER, out.getData());
        verify(localDrisKernelFacade).register(request);
        verify(identifierPermissionService).bindCreatorAfterRegister(TestSamples.VALID_IDENTIFIER);
    }

    @Test
    void register_failed_shouldNotBindCreator() {
        RegistrationRequest request = TestSamples.registrationRequest();
        when(localDrisKernelFacade.register(request)).thenThrow(new RuntimeException("failed"));

        DrisResponse<String> out = service.register(request);

        assertTrue(!out.isOk());
        verify(identifierPermissionService, never()).bindCreatorAfterRegister(TestSamples.VALID_IDENTIFIER);
    }

    @Test
    void registerBatch_empty_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.registerBatch(Collections.<RegistrationRequest>emptyList()));
    }

    @Test
    void registerBatch_valid_shouldDelegateAndBindCreator() {
        when(localDrisKernelFacade.registerBatch(anyList())).thenReturn(Arrays.asList(TestSamples.VALID_IDENTIFIER, TestSamples.VALID_IDENTIFIER_2));
        DrisResponse<java.util.List<String>> out = service.registerBatch(Arrays.asList(TestSamples.registrationRequest(), TestSamples.registrationRequest()));
        assertTrue(out.isOk());
        assertEquals(2, out.getData().size());
        verify(localDrisKernelFacade).registerBatch(anyList());
        verify(identifierPermissionService).bindCreatorAfterRegisterBatch(Arrays.asList(TestSamples.VALID_IDENTIFIER, TestSamples.VALID_IDENTIFIER_2));
    }

    @Test
    void validate_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.validateIdentifier(" "));
    }

    @Test
    void validate_valid_shouldDelegate() {
        when(localDrisKernelFacade.validateIdentifier(TestSamples.VALID_IDENTIFIER)).thenReturn(Boolean.TRUE);
        DrisResponse<Boolean> out = service.validateIdentifier(TestSamples.VALID_IDENTIFIER);
        assertTrue(out.isOk());
        verify(localDrisKernelFacade).validateIdentifier(TestSamples.VALID_IDENTIFIER);
    }

    @Test
    void exists_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.existsIdentifier(""));
    }

    @Test
    void exists_valid_shouldDelegate() {
        when(localDrisKernelFacade.existsIdentifier(TestSamples.VALID_IDENTIFIER)).thenReturn(Boolean.TRUE);
        DrisResponse<Boolean> out = service.existsIdentifier(TestSamples.VALID_IDENTIFIER);
        assertTrue(out.isOk());
        verify(localDrisKernelFacade).existsIdentifier(TestSamples.VALID_IDENTIFIER);
    }

    @Test
    void resolveMetadata_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.resolveMetadata(" "));
    }

    @Test
    void resolveMetadata_valid_shouldDelegate() {
        when(drirpResolutionService.resolveMetadataDistributed(TestSamples.VALID_IDENTIFIER))
                .thenReturn(DrisResponse.success(TestSamples.metadata()));
        DrisResponse<IdentifierMetadata> out = service.resolveMetadata(TestSamples.VALID_IDENTIFIER);
        assertTrue(out.isOk());
        verify(drirpResolutionService).resolveMetadataDistributed(TestSamples.VALID_IDENTIFIER);
    }

    @Test
    void resolveBatch_empty_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.resolveMetadataBatch(Collections.<String>emptyList()));
    }

    @Test
    void resolveBatch_valid_shouldDelegate() {
        when(localDrisKernelFacade.getMetadataBatch(anyList())).thenReturn(Collections.<IdentifierMetadata>emptyList());
        DrisResponse<java.util.List<IdentifierMetadata>> out = service.resolveMetadataBatch(Arrays.asList(TestSamples.VALID_IDENTIFIER, TestSamples.VALID_IDENTIFIER_2));
        assertTrue(out.isOk());
        verify(localDrisKernelFacade).getMetadataBatch(anyList());
    }
    @Test
    void resolveBatchDistributed_empty_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.resolveMetadataBatchDistributed(Collections.<String>emptyList()));
    }

    @Test
    void resolveBatchDistributed_valid_shouldDelegate() {
        when(drirpResolutionService.resolveMetadataBatchDistributed(anyList()))
                .thenReturn(DrisResponse.success(new BatchResolveMetadataResponse()));
        DrisResponse<BatchResolveMetadataResponse> out = service.resolveMetadataBatchDistributed(
                Arrays.asList(TestSamples.VALID_IDENTIFIER, TestSamples.VALID_IDENTIFIER_2)
        );
        assertTrue(out.isOk());
        verify(drirpResolutionService).resolveMetadataBatchDistributed(anyList());
    }
    @Test
    void resolveObjectBatchDistributed_empty_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.resolveDigitalObjectBatchDistributed(Collections.<String>emptyList()));
    }

    @Test
    void resolveObjectBatchDistributed_valid_shouldDelegate() {
        when(drirpResolutionService.resolveDigitalObjectBatchDistributed(anyList()))
                .thenReturn(DrisResponse.success(new BatchResolveDigitalObjectResponse()));
        DrisResponse<BatchResolveDigitalObjectResponse> out = service.resolveDigitalObjectBatchDistributed(
                Arrays.asList(TestSamples.VALID_IDENTIFIER, TestSamples.VALID_IDENTIFIER_2)
        );
        assertTrue(out.isOk());
        verify(drirpResolutionService).resolveDigitalObjectBatchDistributed(anyList());
    }
    @Test
    void getObject_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.getDigitalObject(" "));
    }

    @Test
    void updateIdentifier_nullMetadata_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.updateIdentifier(TestSamples.VALID_IDENTIFIER, null));
    }

    @Test
    void updateIdentifier_mismatchIdentifier_shouldThrow() {
        IdentifierMetadata metadata = TestSamples.metadata();
        metadata.setIdentifier(TestSamples.VALID_IDENTIFIER_2);
        assertThrows(BusinessException.class, () -> service.updateIdentifier(TestSamples.VALID_IDENTIFIER, metadata));
    }

    @Test
    void updateIdentifier_noMetadataId_shouldFillAndDelegate() {
        IdentifierMetadata metadata = TestSamples.metadata();
        metadata.setIdentifier(null);
        when(localDrisKernelFacade.updateMetadata(eq(TestSamples.VALID_IDENTIFIER), any(IdentifierMetadata.class))).thenReturn(Boolean.TRUE);

        DrisResponse<Boolean> out = service.updateIdentifier(TestSamples.VALID_IDENTIFIER, metadata);

        assertTrue(out.isOk());
        assertEquals(TestSamples.VALID_IDENTIFIER, metadata.getIdentifier());
        verify(identifierPermissionService).assertCurrentUserCanWriteIdentifier(TestSamples.VALID_IDENTIFIER);
        verify(localDrisKernelFacade).updateMetadata(TestSamples.VALID_IDENTIFIER, metadata);
    }

    @Test
    void partialUpdate_emptyFields_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.partialUpdateIdentifier(TestSamples.VALID_IDENTIFIER, Collections.<String, String>emptyMap()));
    }

    @Test
    void partialUpdate_valid_shouldDelegate() {
        java.util.Map<String, String> fields = TestSamples.partialFields();
        when(localDrisKernelFacade.patchMetadata(eq(TestSamples.VALID_IDENTIFIER), anyMap())).thenReturn(Boolean.TRUE);

        DrisResponse<Boolean> out = service.partialUpdateIdentifier(TestSamples.VALID_IDENTIFIER, fields);

        assertTrue(out.isOk());
        verify(identifierPermissionService).assertCurrentUserCanWriteIdentifier(TestSamples.VALID_IDENTIFIER);
        verify(localDrisKernelFacade).patchMetadata(TestSamples.VALID_IDENTIFIER, fields);
    }

    @Test
    void delete_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.deleteIdentifier(" "));
    }

    @Test
    void delete_valid_shouldDelegate() {
        when(localDrisKernelFacade.deleteIdentifier(TestSamples.VALID_IDENTIFIER)).thenReturn(Boolean.TRUE);
        DrisResponse<Boolean> out = service.deleteIdentifier(TestSamples.VALID_IDENTIFIER);
        assertTrue(out.isOk());
        verify(identifierPermissionService).assertCurrentUserCanWriteIdentifier(TestSamples.VALID_IDENTIFIER);
        verify(localDrisKernelFacade).deleteIdentifier(TestSamples.VALID_IDENTIFIER);
    }

    @Test
    void getObject_valid_shouldDelegate() {
        when(drirpResolutionService.resolveDigitalObjectDistributed(TestSamples.VALID_IDENTIFIER))
                .thenReturn(DrisResponse.success(new DigitalObject()));
        DrisResponse<DigitalObject> out = service.getDigitalObject(TestSamples.VALID_IDENTIFIER);
        assertTrue(out.isOk());
        verify(drirpResolutionService).resolveDigitalObjectDistributed(TestSamples.VALID_IDENTIFIER);
    }
}




