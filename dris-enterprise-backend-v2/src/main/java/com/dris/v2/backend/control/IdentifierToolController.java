package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.service.IdentifierToolExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/identifiers")
public class IdentifierToolController {
    private final IdentifierToolExecutionService service;

    public IdentifierToolController(IdentifierToolExecutionService service) {
        this.service = service;
    }

    @PostMapping("/{identifier}/tool/execute")
    public ResponseEntity<ApiResponse<ToolExecuteResult>> executeTool(
            @PathVariable("identifier") String identifier,
            @RequestBody(required = false) ToolExecuteRequest request
    ) {
        return ApiResponses.fromDris(service.executeTool(identifier, request));
    }
}

