package com.dris.v2.backend.service;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.dto.BatchResolveResourceResponse;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.contract.DrisResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifierResourceServiceTest {
    @Mock
    private LocalDrisKernelFacade localDrisKernelFacade;
    @Mock
    private DrirpResolutionService drirpResolutionService;
    @Mock
    private IdentifierPermissionService identifierPermissionService;
    @Mock
    private IdentifierBusinessAuditService identifierBusinessAuditService;
    @InjectMocks
    private IdentifierResourceService service;

    @Test
    void getComponent_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.getComponent(" "));
    }

    @Test
    void upsertComponent_nullResource_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.upsertComponent(TestSamples.VALID_IDENTIFIER, null, false));
    }

    @Test
    void removeComponent_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.removeComponent(""));
    }

    @Test
    void resolveResourceBatchDistributed_empty_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.resolveResourceBatchDistributed(Collections.<String>emptyList()));
    }

    @Test
    void upsertComponent_valid_shouldDelegate() {
        Resource resource = TestSamples.resource();
        when(localDrisKernelFacade.upsertResource(TestSamples.VALID_IDENTIFIER, resource, true)).thenReturn(Boolean.TRUE);

        DrisResponse<Boolean> out = service.upsertComponent(TestSamples.VALID_IDENTIFIER, resource, true);

        assertTrue(out.isOk());
        verify(identifierPermissionService).assertCurrentUserCanWriteIdentifier(TestSamples.VALID_IDENTIFIER);
        verify(localDrisKernelFacade).upsertResource(TestSamples.VALID_IDENTIFIER, resource, true);
    }

    @Test
    void getComponent_valid_shouldDelegate() {
        when(drirpResolutionService.resolveResourceDistributed(TestSamples.VALID_IDENTIFIER))
                .thenReturn(DrisResponse.success(TestSamples.resource()));
        DrisResponse<Resource> out = service.getComponent(TestSamples.VALID_IDENTIFIER);
        assertTrue(out.isOk());
        verify(drirpResolutionService).resolveResourceDistributed(TestSamples.VALID_IDENTIFIER);
    }

    @Test
    void resolveResourceBatchDistributed_valid_shouldDelegate() {
        when(drirpResolutionService.resolveResourceBatchDistributed(anyList()))
                .thenReturn(DrisResponse.success(new BatchResolveResourceResponse()));
        DrisResponse<BatchResolveResourceResponse> out = service.resolveResourceBatchDistributed(
                Arrays.asList(TestSamples.VALID_IDENTIFIER, TestSamples.VALID_IDENTIFIER_2)
        );
        assertTrue(out.isOk());
        verify(drirpResolutionService).resolveResourceBatchDistributed(anyList());
    }

    @Test
    void removeComponent_valid_shouldDelegate() {
        when(localDrisKernelFacade.removeResource(TestSamples.VALID_IDENTIFIER)).thenReturn(Boolean.TRUE);
        DrisResponse<Boolean> out = service.removeComponent(TestSamples.VALID_IDENTIFIER);
        assertTrue(out.isOk());
        verify(identifierPermissionService).assertCurrentUserCanWriteIdentifier(TestSamples.VALID_IDENTIFIER);
        verify(localDrisKernelFacade).removeResource(TestSamples.VALID_IDENTIFIER);
    }
}
