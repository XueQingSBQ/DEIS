package com.dris.v2.backend.service;

import com.dris.v2.backend.common.concurrent.AsyncExecutionSupport;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.config.WorkloadExecutorConfiguration;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectResponse;
import com.dris.v2.backend.dto.BatchResolveMetadataResponse;
import com.dris.v2.backend.dto.BatchResolveResourceResponse;
import com.dris.v2.backend.model.ResolveForwardRequestPayload;
import com.dris.v2.backend.model.ResolveForwardResponsePayload;
import com.dris.v2.backend.model.ResolveType;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import com.dris.v2.backend.service.resolve.DistributedBatchResolutionSupport;
import com.dris.v2.backend.service.resolve.IterativeResolutionStrategy;
import com.dris.v2.backend.service.resolve.RecursiveResolutionStrategy;
import com.dris.v2.backend.service.resolve.ResolutionForwardHop;
import com.dris.v2.backend.service.resolve.ResolutionStrategy;
import com.dris.v2.backend.service.resolve.ResolutionSupport;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.google.gson.Gson;
import org.bdware.drirp.model.DrirpMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

@Service
public class DrirpResolutionService {
    private final BackendV2Properties properties;
    private final ResolutionSupport support;
    private final ResolutionStrategy recursiveStrategy;
    private final ResolutionStrategy iterativeStrategy;
    private final DistributedBatchResolutionSupport batchSupport;
    private final Gson gson = new Gson();

    public DrirpResolutionService(
            LocalDrisKernelFacade localDrisKernelFacade,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService
    ) {
        this(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService,
                AsyncExecutionSupport.directExecutor()
        );
    }

    @Autowired
    public DrirpResolutionService(
            LocalDrisKernelFacade localDrisKernelFacade,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService,
            @Qualifier(WorkloadExecutorConfiguration.RESOLVE_EXECUTOR) Executor resolveExecutor
    ) {
        this.properties = properties;
        this.support = new ResolutionSupport(
                localDrisKernelFacade,
                properties,
                routeForwardDao,
                directDescendantDao,
                drirpSecuritySupport,
                drirpAuditService
        );
        this.recursiveStrategy = new RecursiveResolutionStrategy(this.support);
        this.iterativeStrategy = new IterativeResolutionStrategy(this.support);
        this.batchSupport = new DistributedBatchResolutionSupport(properties, this.support, resolveExecutor);
    }

    public DrisResponse<IdentifierMetadata> resolveMetadataDistributed(String identifier) {
        return resolveDistributed(identifier, ResolveType.METADATA, IdentifierMetadata.class);
    }

    public DrisResponse<BatchResolveMetadataResponse> resolveMetadataBatchDistributed(List<String> identifiers) {
        return batchSupport.resolveMetadataBatchDistributed(identifiers);
    }

    public DrisResponse<DigitalObject> resolveDigitalObjectDistributed(String identifier) {
        return resolveDistributed(identifier, ResolveType.DIGITAL_OBJECT, DigitalObject.class);
    }

    public DrisResponse<BatchResolveDigitalObjectResponse> resolveDigitalObjectBatchDistributed(List<String> identifiers) {
        return batchSupport.resolveDigitalObjectBatchDistributed(identifiers);
    }

    public DrisResponse<Resource> resolveResourceDistributed(String identifier) {
        return resolveDistributed(identifier, ResolveType.RESOURCE, Resource.class);
    }

    public DrisResponse<BatchResolveResourceResponse> resolveResourceBatchDistributed(List<String> identifiers) {
        return batchSupport.resolveResourceBatchDistributed(identifiers);
    }

    public <T> DrisResponse<T> resolveDistributed(String identifier, ResolveType resolveType, Class<T> dataType) {
        String id = support.trim(identifier);
        if (support.isBlank(id)) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier is required", null);
        }
        if (!support.isIdentifierValid(id)) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier format is invalid", null);
        }

        ResolveType type = resolveType == null ? ResolveType.METADATA : resolveType;
        String targetCode = support.extractTargetCode(id);
        if (targetCode == null) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifier format is invalid", null);
        }
        if (!properties.isDrirpEnabled() || support.isLocalTarget(targetCode)) {
            return support.resolveLocal(id, type, dataType);
        }

        ResolutionForwardHop firstHop = support.resolveNextHop(targetCode);
        if (firstHop == null || support.isBlank(firstHop.getNextHopUrl())) {
            return DrisResponse.failure("ROUTE_NOT_FOUND", "route not found for target code: " + targetCode, null);
        }
        return selectStrategy().resolve(id, type, dataType, firstHop);
    }

    public DrirpMessage handleResolveForward(DrirpMessage request) {
        ResolveForwardRequestPayload payload = support.toRequestPayload(request == null ? null : request.getBody());
        String identifier = support.trim(payload.getIdentifier());
        if (support.isBlank(identifier)) {
            return support.error(request, "VALIDATION_ERROR", "identifier is required");
        }
        if (!support.isIdentifierValid(identifier)) {
            return support.error(request, "VALIDATION_ERROR", "identifier format is invalid");
        }

        ResolveType resolveType = ResolveType.parse(payload.getResolveType());
        List<String> visitedUrls = support.sanitizeUrls(payload.getVisitedUrls());
        String selfUrl = support.resolveSelfDrirpUrl();
        if (support.contains(visitedUrls, selfUrl)) {
            return support.error(request, "LOOP_DETECTED", "resolve loop detected");
        }
        List<String> visitedNext = support.append(visitedUrls, selfUrl);

        int ttl = payload.getTtl() == null ? support.normalizeMaxHops() : payload.getTtl().intValue();
        String targetCode = support.extractTargetCode(identifier);
        if (targetCode == null) {
            return support.error(request, "VALIDATION_ERROR", "identifier format is invalid");
        }

        if (support.isLocalTarget(targetCode)) {
            DrisResponse<?> local = support.resolveLocalRaw(identifier, resolveType);
            if (local == null || !local.isOk()) {
                return support.error(
                        request,
                        local == null ? "UPSTREAM_ERROR" : local.getCode(),
                        local == null ? "local resolve failed" : local.getMessage()
                );
            }
            ResolveForwardResponsePayload out = new ResolveForwardResponsePayload();
            out.setResolveType(resolveType.name());
            out.setResolvedBy(support.localNodeCode());
            out.setMode(support.resolveMode().name().toLowerCase());
            out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl)));
            out.setVisitedUrls(visitedNext);
            if (resolveType == ResolveType.DIGITAL_OBJECT) {
                out.setDigitalObject(support.castData(local.getData(), DigitalObject.class));
            } else if (resolveType == ResolveType.RESOURCE) {
                out.setResource(support.castData(local.getData(), Resource.class));
            } else {
                out.setMetadata(support.castData(local.getData(), IdentifierMetadata.class));
            }
            return support.ok(request, gson.toJsonTree(out));
        }

        if (ttl <= 0) {
            return support.error(request, "TTL_EXCEEDED", "resolve ttl exceeded");
        }

        ResolutionForwardHop hop = support.resolveNextHop(targetCode);
        if (hop == null || support.isBlank(hop.getNextHopUrl())) {
            return support.error(request, "ROUTE_NOT_FOUND", "route not found for target code: " + targetCode);
        }
        if (support.contains(visitedNext, hop.getNextHopUrl())) {
            return support.error(request, "LOOP_DETECTED", "next hop already visited");
        }

        if (support.resolveMode() == com.dris.v2.backend.service.resolve.ResolveMode.ITERATIVE) {
            ResolveForwardResponsePayload out = new ResolveForwardResponsePayload();
            out.setDelegate(Boolean.TRUE);
            out.setResolveType(resolveType.name());
            out.setNextHopCode(support.blankIfNull(hop.getNextHopCode()));
            out.setNextHopDrirpUrl(hop.getNextHopUrl());
            out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl - 1)));
            out.setVisitedUrls(visitedNext);
            return support.ok(request, gson.toJsonTree(out));
        }

        ResolveForwardRequestPayload forwardPayload = new ResolveForwardRequestPayload();
        forwardPayload.setIdentifier(identifier);
        forwardPayload.setResolveType(resolveType.name());
        forwardPayload.setTtl(Integer.valueOf(ttl - 1));
        forwardPayload.setVisitedUrls(visitedNext);

        DrirpMessage response = support.sendForward(hop.getNextHopUrl(), request, forwardPayload);
        if (response == null) {
            return support.error(request, "UPSTREAM_ERROR", "upstream response is null");
        }
        if (response.getRid() == null && request != null) {
            response.setRid(request.getRid());
        }
        if (response.getCode() == 0 && request != null) {
            response.setCode(request.getCode());
        }
        return response;
    }

    public DrirpMessage handleResolveBatchForward(DrirpMessage request) {
        return batchSupport.handleResolveBatchForward(request);
    }

    private ResolutionStrategy selectStrategy() {
        return support.resolveMode() == com.dris.v2.backend.service.resolve.ResolveMode.ITERATIVE
                ? iterativeStrategy
                : recursiveStrategy;
    }
}