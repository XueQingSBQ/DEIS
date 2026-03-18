package com.dris.v2.backend.service.operation;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;

import java.util.Collections;

public class RecursiveOperationStrategy implements OperationStrategy {
    private final OperationSupport support;

    public RecursiveOperationStrategy(OperationSupport support) {
        this.support = support;
    }

    @Override
    public DrisResponse<ToolExecuteResult> executeTool(
            String identifier,
            ToolExecuteRequest request,
            String userId,
            OperationForwardHop firstHop
    ) {
        try {
            DrirpMessage response = support.sendForward(
                    firstHop.getNextHopUrl(),
                    null,
                    support.buildToolExecutePayload(
                            identifier,
                            userId,
                            support.normalizeMaxHops(),
                            Collections.<String>emptyList(),
                            request
                    )
            );
            return support.parseToolExecuteResponse(response);
        } catch (DrirpClientException e) {
            return DrisResponse.failure("UPSTREAM_ERROR", e.getMessage(), null);
        }
    }

    @Override
    public DrisResponse<DownloadPresignResult> createDownloadPresign(
            DownloadPresignRequest request,
            String userId,
            OperationForwardHop firstHop
    ) {
        try {
            DrirpMessage response = support.sendForward(
                    firstHop.getNextHopUrl(),
                    null,
                    support.buildDownloadPresignPayload(
                            request.getIdentifier(),
                            userId,
                            support.normalizeMaxHops(),
                            Collections.<String>emptyList(),
                            request
                    )
            );
            return support.parseDownloadPresignResponse(response);
        } catch (DrirpClientException e) {
            return DrisResponse.failure("UPSTREAM_ERROR", e.getMessage(), null);
        }
    }
}
