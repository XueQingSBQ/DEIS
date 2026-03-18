package com.dris.v2.backend.service;

import com.dris.v2.backend.auth.CurrentUserContext;
import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.model.OperationForwardRequestPayload;
import com.dris.v2.backend.model.OperationForwardResponsePayload;
import com.dris.v2.backend.model.OperationType;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.service.operation.IterativeOperationStrategy;
import com.dris.v2.backend.service.operation.OperationForwardHop;
import com.dris.v2.backend.service.operation.OperationStrategy;
import com.dris.v2.backend.service.operation.OperationSupport;
import com.dris.v2.backend.service.operation.RecursiveOperationStrategy;
import com.dris.v2.backend.service.resolve.ResolveMode;
import com.dris.v2.backend.risk.RiskDecisionService;
import com.dris.v2.backend.service.local.upload.LocalUploadService;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DrirpOperationService {
    private final BackendV2Properties properties;
    private final OperationSupport support;
    private final OperationStrategy recursiveStrategy;
    private final OperationStrategy iterativeStrategy;

    public DrirpOperationService(
            LocalUploadService localUploadService,
            ToolExecutionLocalService toolExecutionLocalService,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            RiskDecisionService riskDecisionService,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService
    ) {
        this.properties = properties;
        this.support = new OperationSupport(
                localUploadService,
                toolExecutionLocalService,
                properties,
                routeForwardDao,
                directDescendantDao,
                riskDecisionService,
                drirpSecuritySupport,
                drirpAuditService
        );
        this.recursiveStrategy = new RecursiveOperationStrategy(this.support);
        this.iterativeStrategy = new IterativeOperationStrategy(this.support);
    }

    public DrisResponse<ToolExecuteResult> executeToolDistributed(String identifier, ToolExecuteRequest request) {
        String id = support.trim(identifier);
        if (support.isBlank(id)) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier is required", null);
        }
        if (!support.isIdentifierValid(id)) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier format is invalid", null);
        }

        String targetCode = support.extractTargetCode(id);
        if (targetCode == null) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier format is invalid", null);
        }
        if (!properties.isDrirpEnabled() || support.isLocalTarget(targetCode)) {
            return support.executeToolLocal(CurrentUserContext.getUserIdOrNull(), id, request);
        }

        OperationForwardHop firstHop = support.resolveNextHop(targetCode);
        if (firstHop == null || support.isBlank(firstHop.getNextHopUrl())) {
            return DrisResponse.failure("NOT_FOUND", "route not found for target code: " + targetCode, null);
        }
        return selectStrategy().executeTool(id, request, CurrentUserContext.getUserIdOrNull(), firstHop);
    }

    public DrisResponse<DownloadPresignResult> createDownloadPresignDistributed(DownloadPresignRequest request) {
        DownloadPresignRequest normalized = support.normalizePresignRequest(request);
        if (normalized == null) {
            return DrisResponse.failure("VALIDATION_ERROR", "request is required", null);
        }

        String id = support.trim(normalized.getIdentifier());
        if (support.isBlank(id)) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier is required", null);
        }
        if (!support.isIdentifierValid(id)) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier format is invalid", null);
        }
        normalized.setIdentifier(id);

        String targetCode = support.extractTargetCode(id);
        if (targetCode == null) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier format is invalid", null);
        }
        if (!properties.isDrirpEnabled() || support.isLocalTarget(targetCode)) {
            return support.createDownloadPresignLocal(CurrentUserContext.getUserIdOrNull(), id, normalized);
        }

        OperationForwardHop firstHop = support.resolveNextHop(targetCode);
        if (firstHop == null || support.isBlank(firstHop.getNextHopUrl())) {
            return DrisResponse.failure("NOT_FOUND", "route not found for target code: " + targetCode, null);
        }
        return selectStrategy().createDownloadPresign(normalized, CurrentUserContext.getUserIdOrNull(), firstHop);
    }

    public DrirpMessage handleOperationForward(DrirpMessage request) {
        OperationForwardRequestPayload payload = support.toRequestPayload(request == null ? null : request.getBody());
        String identifier = support.trim(payload.getIdentifier());
        if (support.isBlank(identifier)) {
            return support.error(request, "VALIDATION_ERROR", "identifier is required");
        }
        if (!support.isIdentifierValid(identifier)) {
            return support.error(request, "VALIDATION_ERROR", "identifier format is invalid");
        }

        OperationType operationType = OperationType.parse(payload.getOperationType());
        if (operationType == null) {
            return support.error(request, "VALIDATION_ERROR", "operationType is invalid");
        }

        List<String> visitedUrls = support.sanitizeUrls(payload.getVisitedUrls());
        String selfUrl = support.resolveSelfDrirpUrl();
        if (support.contains(visitedUrls, selfUrl)) {
            return support.error(request, "LOOP_DETECTED", "operation loop detected");
        }
        List<String> visitedNext = support.append(visitedUrls, selfUrl);

        int ttl = payload.getTtl() == null ? support.normalizeMaxHops() : payload.getTtl().intValue();
        String targetCode = support.extractTargetCode(identifier);
        if (targetCode == null) {
            return support.error(request, "VALIDATION_ERROR", "identifier format is invalid");
        }

        if (support.isLocalTarget(targetCode)) {
            return handleLocalOperationForward(request, payload, identifier, operationType, ttl, visitedNext);
        }

        if (ttl <= 0) {
            return support.error(request, "TTL_EXCEEDED", "ttl exceeded before reaching target");
        }

        OperationForwardHop nextHop = support.resolveNextHop(targetCode);
        if (nextHop == null || support.isBlank(nextHop.getNextHopUrl())) {
            return support.error(request, "NOT_FOUND", "route not found for target code: " + targetCode);
        }

        if (support.resolveMode() == ResolveMode.ITERATIVE) {
            OperationForwardResponsePayload out = new OperationForwardResponsePayload();
            out.setDelegate(Boolean.TRUE);
            out.setOperationType(operationType.name());
            out.setNextHopCode(nextHop.getNextHopCode());
            out.setNextHopDrirpUrl(nextHop.getNextHopUrl());
            out.setTtlRemaining(Integer.valueOf(ttl - 1));
            out.setVisitedUrls(visitedNext);
            return support.ok(request, support.toJsonTree(out));
        }

        OperationForwardRequestPayload forwardPayload = operationType == OperationType.DOWNLOAD_PRESIGN
                ? support.buildDownloadPresignPayload(
                        identifier,
                        support.trim(payload.getUserId()),
                        ttl - 1,
                        visitedNext,
                        payload.getDownloadPresignRequest()
                )
                : support.buildToolExecutePayload(
                        identifier,
                        support.trim(payload.getUserId()),
                        ttl - 1,
                        visitedNext,
                        payload.getToolExecuteRequest()
                );
        try {
            return support.sendForward(nextHop.getNextHopUrl(), request, forwardPayload);
        } catch (DrirpClientException e) {
            return support.error(request, "UPSTREAM_ERROR", e.getMessage());
        }
    }

    private DrirpMessage handleLocalOperationForward(
            DrirpMessage request,
            OperationForwardRequestPayload payload,
            String identifier,
            OperationType operationType,
            int ttl,
            List<String> visitedNext
    ) {
        OperationForwardResponsePayload out = new OperationForwardResponsePayload();
        out.setOperationType(operationType.name());
        out.setHandledBy(support.localNodeCode());
        out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl)));
        out.setVisitedUrls(visitedNext);

        if (operationType == OperationType.TOOL_EXECUTE) {
            DrisResponse<ToolExecuteResult> local = support.executeToolLocal(
                    support.trim(payload.getUserId()),
                    identifier,
                    payload.getToolExecuteRequest()
            );
            if (local == null || !local.isOk()) {
                return support.error(
                        request,
                        local == null ? "UPSTREAM_ERROR" : local.getCode(),
                        local == null ? "local operation failed" : local.getMessage()
                );
            }
            out.setToolExecuteResult(local.getData());
            return support.ok(request, support.toJsonTree(out));
        }

        if (operationType == OperationType.DOWNLOAD_PRESIGN) {
            DrisResponse<DownloadPresignResult> local = support.createDownloadPresignLocal(
                    support.trim(payload.getUserId()),
                    identifier,
                    payload.getDownloadPresignRequest()
            );
            if (local == null || !local.isOk()) {
                return support.error(
                        request,
                        local == null ? "UPSTREAM_ERROR" : local.getCode(),
                        local == null ? "local operation failed" : local.getMessage()
                );
            }
            out.setDownloadPresignResult(local.getData());
            return support.ok(request, support.toJsonTree(out));
        }

        return support.error(request, "VALIDATION_ERROR", "unsupported operationType");
    }

    private OperationStrategy selectStrategy() {
        return support.resolveMode() == ResolveMode.ITERATIVE ? iterativeStrategy : recursiveStrategy;
    }
}
