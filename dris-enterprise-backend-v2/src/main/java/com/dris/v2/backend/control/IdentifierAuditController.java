package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.dto.IdentifierHistoryPageResponse;
import com.dris.v2.backend.service.IdentifierAuditQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/audit")
public class IdentifierAuditController {
    private final IdentifierAuditQueryService identifierAuditQueryService;

    public IdentifierAuditController(IdentifierAuditQueryService identifierAuditQueryService) {
        this.identifierAuditQueryService = identifierAuditQueryService;
    }

    @GetMapping("/identifier-history")
    public ResponseEntity<ApiResponse<IdentifierHistoryPageResponse>> identifierHistory(
            @RequestParam(value = "identifier", required = false) String identifier,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ApiResponses.ok(identifierAuditQueryService.queryIdentifierHistory(
                identifier,
                operation,
                startTime,
                endTime,
                page,
                size
        ));
    }
}
