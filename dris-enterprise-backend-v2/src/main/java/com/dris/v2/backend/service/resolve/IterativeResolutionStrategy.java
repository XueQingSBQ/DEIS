package com.dris.v2.backend.service.resolve;

import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.model.ResolveForwardRequestPayload;
import com.dris.v2.backend.model.ResolveType;
import org.bdware.drirp.client.DrirpRouterClientImpl;
import org.bdware.drirp.model.DrirpMessage;

import java.util.Collections;

public class IterativeResolutionStrategy implements ResolutionStrategy {
    private final ResolutionSupport support;

    public IterativeResolutionStrategy(ResolutionSupport support) {
        this.support = support;
    }

    @Override
    public <T> DrisResponse<T> resolve(String identifier, ResolveType resolveType, Class<T> dataType, ResolutionForwardHop firstHop) {
        DrirpRouterClientImpl routerClient = null;
        long startedAt = System.currentTimeMillis();
        boolean success = false;
        int status = 502;
        String errorMessage = "";
        String traceId = null;
        try {
            routerClient = support.createClientBuilder(firstHop.getNextHopUrl()).buildRouterAndConnect();
            routerClient.setMaxHops(support.normalizeMaxHops());
            routerClient.setDelegateKeys(
                    "nextHopDrirpUrl",
                    "next_hop_drirp_url",
                    "nextHopUrl",
                    "nextUrl",
                    "delegateUrl",
                    "routerUrl",
                    "next"
            );

            ResolveForwardRequestPayload payload = new ResolveForwardRequestPayload();
            payload.setIdentifier(identifier);
            payload.setResolveType(resolveType.name());
            payload.setTtl(Integer.valueOf(support.normalizeMaxHops()));
            payload.setVisitedUrls(support.append(Collections.<String>emptyList(), support.resolveSelfDrirpUrl()));

            DrirpMessage request = new DrirpMessage(NodeDrirpOpcode.RESOLVE_FORWARD, support.toJsonTree(payload));
            request.ensureProtocolDefaults("v1");
            DrirpMessage response = routerClient.send(request);
            traceId = response == null ? request.getTraceId() : response.getTraceId();
            success = response != null && response.isSuccess();
            status = success ? 200 : 502;
            if (!success) {
                errorMessage = response == null
                        ? "upstream response is null"
                        : support.blankIfNull(response.getErrorMessage(), support.blankIfNull(response.getErrorCode(), "upstream error"));
            }
            return support.parseResolveResponse(response, resolveType, dataType);
        } catch (Exception e) {
            String detail = e.getClass().getName();
            if (!support.isBlank(e.getMessage())) {
                detail = detail + ": " + e.getMessage();
            }
            errorMessage = detail;
            return DrisResponse.failure("UPSTREAM_ERROR", "iterative resolve failed: " + detail, null);
        } finally {
            support.recordOutboundAudit(
                    "RESOLVE_FORWARD_OUT",
                    traceId,
                    firstHop == null ? "" : firstHop.getNextHopUrl(),
                    status,
                    System.currentTimeMillis() - startedAt,
                    success,
                    errorMessage
            );
            if (routerClient != null) {
                routerClient.close();
            }
        }
    }
}
