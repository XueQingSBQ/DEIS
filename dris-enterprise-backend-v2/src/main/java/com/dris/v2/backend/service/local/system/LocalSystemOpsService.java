package com.dris.v2.backend.service.local.system;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.system.VectorSyncResult;

import java.util.Map;

public interface LocalSystemOpsService {
    DrisResponse<VectorSyncResult> syncVectorIndex();

    Map<String, Map<String, Long>> metricsSnapshot();
}