package com.dris.v2.backend.service;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.contract.DrisResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdentifierToolExecutionService {
    private final DrirpOperationService drirpOperationService;

    public IdentifierToolExecutionService(DrirpOperationService drirpOperationService) {
        this.drirpOperationService = drirpOperationService;
    }

    public DrisResponse<ToolExecuteResult> executeTool(String identifier, ToolExecuteRequest request) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", "identifier is required", HttpStatus.BAD_REQUEST);
        }
        return drirpOperationService.executeToolDistributed(identifier.trim(), request);
    }
}
