package com.dris.v2.backend.control;

import com.dris.v2.backend.api.ApiResponses;
import com.dris.v2.backend.dto.ApiResponse;
import com.dris.v2.backend.service.SystemOpsService;
import com.dris.v2.backend.contract.system.VectorSyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/ops")
public class SystemOpsController {
    private final SystemOpsService service;

    public SystemOpsController(SystemOpsService service) {
        this.service = service;
    }

    @PostMapping("/vector/sync")
    public ResponseEntity<ApiResponse<VectorSyncResult>> syncVector() {
        return ApiResponses.fromDris(service.syncVectorIndex());
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Long>>>> metrics() {
        return ApiResponses.ok(service.metricsSnapshot());
    }
}
