package com.dris.v2.backend.service;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.system.VectorSyncResult;
import com.dris.v2.backend.service.local.system.LocalSystemOpsService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SystemOpsService {
    private final LocalSystemOpsService localSystemOpsService;

    public SystemOpsService(LocalSystemOpsService localSystemOpsService) {
        this.localSystemOpsService = localSystemOpsService;
    }

    public DrisResponse<VectorSyncResult> syncVectorIndex() {
        return localSystemOpsService.syncVectorIndex();
    }

    public Map<String, Map<String, Long>> metricsSnapshot() {
        return localSystemOpsService.metricsSnapshot();
    }
}