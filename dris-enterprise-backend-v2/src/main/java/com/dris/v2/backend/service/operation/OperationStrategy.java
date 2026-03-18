package com.dris.v2.backend.service.operation;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;

public interface OperationStrategy {
    DrisResponse<ToolExecuteResult> executeTool(
            String identifier,
            ToolExecuteRequest request,
            String userId,
            OperationForwardHop firstHop
    );

    DrisResponse<DownloadPresignResult> createDownloadPresign(
            DownloadPresignRequest request,
            String userId,
            OperationForwardHop firstHop
    );
}
