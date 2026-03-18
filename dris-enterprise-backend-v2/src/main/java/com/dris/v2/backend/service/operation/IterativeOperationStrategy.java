package com.dris.v2.backend.service.operation;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.model.OperationForwardResponsePayload;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;

import java.util.ArrayList;
import java.util.List;

public class IterativeOperationStrategy implements OperationStrategy {
    private final OperationSupport support;

    public IterativeOperationStrategy(OperationSupport support) {
        this.support = support;
    }

    @Override
    public DrisResponse<ToolExecuteResult> executeTool(
            String identifier,
            ToolExecuteRequest request,
            String userId,
            OperationForwardHop firstHop
    ) {
        OperationForwardHop currentHop = firstHop;
        int ttl = support.normalizeMaxHops();
        List<String> visitedUrls = new ArrayList<String>();
        while (ttl > 0) {
            try {
                DrirpMessage response = support.sendForward(
                        currentHop.getNextHopUrl(),
                        null,
                        support.buildToolExecutePayload(identifier, userId, ttl, visitedUrls, request)
                );
                if (response == null) {
                    return DrisResponse.failure("UPSTREAM_ERROR", "upstream response is null", null);
                }
                OperationForwardResponsePayload body = support.toResponsePayload(response.getBody());
                if (Boolean.TRUE.equals(body.getDelegate())) {
                    if (support.isBlank(body.getNextHopDrirpUrl())) {
                        return DrisResponse.failure("PROTOCOL_ERROR", "delegate response missing next hop url", null);
                    }
                    currentHop = new OperationForwardHop(body.getNextHopCode(), support.trim(body.getNextHopDrirpUrl()));
                    visitedUrls = support.sanitizeUrls(body.getVisitedUrls());
                    ttl = body.getTtlRemaining() == null ? ttl - 1 : body.getTtlRemaining().intValue();
                    continue;
                }
                return support.parseToolExecuteResponse(response);
            } catch (DrirpClientException e) {
                return DrisResponse.failure("UPSTREAM_ERROR", "iterative operation failed: " + e.getMessage(), null);
            }
        }
        return DrisResponse.failure("TTL_EXCEEDED", "ttl exceeded before reaching target", null);
    }

    @Override
    public DrisResponse<DownloadPresignResult> createDownloadPresign(
            DownloadPresignRequest request,
            String userId,
            OperationForwardHop firstHop
    ) {
        OperationForwardHop currentHop = firstHop;
        int ttl = support.normalizeMaxHops();
        List<String> visitedUrls = new ArrayList<String>();
        while (ttl > 0) {
            try {
                DrirpMessage response = support.sendForward(
                        currentHop.getNextHopUrl(),
                        null,
                        support.buildDownloadPresignPayload(request.getIdentifier(), userId, ttl, visitedUrls, request)
                );
                if (response == null) {
                    return DrisResponse.failure("UPSTREAM_ERROR", "upstream response is null", null);
                }
                OperationForwardResponsePayload body = support.toResponsePayload(response.getBody());
                if (Boolean.TRUE.equals(body.getDelegate())) {
                    if (support.isBlank(body.getNextHopDrirpUrl())) {
                        return DrisResponse.failure("PROTOCOL_ERROR", "delegate response missing next hop url", null);
                    }
                    currentHop = new OperationForwardHop(body.getNextHopCode(), support.trim(body.getNextHopDrirpUrl()));
                    visitedUrls = support.sanitizeUrls(body.getVisitedUrls());
                    ttl = body.getTtlRemaining() == null ? ttl - 1 : body.getTtlRemaining().intValue();
                    continue;
                }
                return support.parseDownloadPresignResponse(response);
            } catch (DrirpClientException e) {
                return DrisResponse.failure("UPSTREAM_ERROR", "iterative operation failed: " + e.getMessage(), null);
            }
        }
        return DrisResponse.failure("TTL_EXCEEDED", "ttl exceeded before reaching target", null);
    }
}
