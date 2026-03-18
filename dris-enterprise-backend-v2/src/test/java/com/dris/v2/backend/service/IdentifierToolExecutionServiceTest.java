package com.dris.v2.backend.service;

import com.dris.v2.backend.TestSamples;
import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.contract.DrisResponse;
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
class IdentifierToolExecutionServiceTest {
    @Mock
    private DrirpOperationService drirpOperationService;
    @InjectMocks
    private IdentifierToolExecutionService service;

    @Test
    void executeTool_blankIdentifier_shouldThrow() {
        assertThrows(BusinessException.class, () -> service.executeTool(" ", new ToolExecuteRequest()));
    }

    @Test
    void executeTool_valid_shouldDelegateToDistributedOperationService() {
        ToolExecuteResult result = new ToolExecuteResult();
        result.setIdentifier(TestSamples.VALID_IDENTIFIER);
        result.setSuccess(true);
        when(drirpOperationService.executeToolDistributed(TestSamples.VALID_IDENTIFIER, null))
                .thenReturn(DrisResponse.success(result));

        DrisResponse<ToolExecuteResult> out = service.executeTool(TestSamples.VALID_IDENTIFIER, null);

        assertTrue(out.isOk());
        verify(drirpOperationService).executeToolDistributed(TestSamples.VALID_IDENTIFIER, null);
    }
}
