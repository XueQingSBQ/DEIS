package com.dris.v2.backend.risk;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;

public interface RiskDecisionService {
    RiskDecisionResult evaluateDownload(String userId, String identifier, DownloadPresignRequest request);

    RiskDecisionResult evaluateToolExecution(String userId, String identifier, ToolExecuteRequest request);
}
