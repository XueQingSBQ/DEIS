package com.dris.v2.backend.risk;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import org.springframework.stereotype.Service;

@Service
public class PermitAllRiskDecisionService implements RiskDecisionService {
    @Override
    public RiskDecisionResult evaluateDownload(String userId, String identifier, DownloadPresignRequest request) {
        return RiskDecisionResult.allow("permit-all");
    }

    @Override
    public RiskDecisionResult evaluateToolExecution(String userId, String identifier, ToolExecuteRequest request) {
        return RiskDecisionResult.allow("permit-all");
    }
}
