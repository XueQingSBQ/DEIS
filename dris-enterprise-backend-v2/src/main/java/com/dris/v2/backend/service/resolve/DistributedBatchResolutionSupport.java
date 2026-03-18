package com.dris.v2.backend.service.resolve;

import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.common.concurrent.AsyncExecutionSupport;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectItem;
import com.dris.v2.backend.dto.BatchResolveDigitalObjectResponse;
import com.dris.v2.backend.dto.BatchResolveMetadataItem;
import com.dris.v2.backend.dto.BatchResolveMetadataResponse;
import com.dris.v2.backend.dto.BatchResolveResourceItem;
import com.dris.v2.backend.dto.BatchResolveResourceResponse;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.model.ResolveBatchForwardRequestPayload;
import com.dris.v2.backend.model.ResolveBatchForwardResponsePayload;
import com.dris.v2.backend.model.ResolveBatchItemPayload;
import com.dris.v2.backend.model.ResolveType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.bdware.drirp.client.DrirpClientImpl;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class DistributedBatchResolutionSupport {
    private static final int MAX_BATCH_SIZE = 200;

    private final BackendV2Properties properties;
    private final ResolutionSupport support;
    private final Executor resolveExecutor;
    private final Gson gson = new Gson();

    public DistributedBatchResolutionSupport(BackendV2Properties properties, ResolutionSupport support) {
        this(properties, support, AsyncExecutionSupport.directExecutor());
    }

    public DistributedBatchResolutionSupport(BackendV2Properties properties, ResolutionSupport support, Executor resolveExecutor) {
        this.properties = properties;
        this.support = support;
        this.resolveExecutor = resolveExecutor == null ? AsyncExecutionSupport.directExecutor() : resolveExecutor;
    }

    public DrisResponse<BatchResolveMetadataResponse> resolveMetadataBatchDistributed(List<String> identifiers) {
        DrisResponse<BatchResolveMetadataResponse> validation = validateBatchRequest(identifiers);
        if (validation != null) {
            return validation;
        }
        return DrisResponse.success(toMetadataBatchResponse(resolveBatchDistributedInternal(identifiers, ResolveType.METADATA)));
    }

    public DrisResponse<BatchResolveDigitalObjectResponse> resolveDigitalObjectBatchDistributed(List<String> identifiers) {
        DrisResponse<BatchResolveDigitalObjectResponse> validation = validateBatchRequest(identifiers);
        if (validation != null) {
            return validation;
        }
        return DrisResponse.success(toDigitalObjectBatchResponse(resolveBatchDistributedInternal(identifiers, ResolveType.DIGITAL_OBJECT)));
    }

    public DrisResponse<BatchResolveResourceResponse> resolveResourceBatchDistributed(List<String> identifiers) {
        DrisResponse<BatchResolveResourceResponse> validation = validateBatchRequest(identifiers);
        if (validation != null) {
            return validation;
        }
        return DrisResponse.success(toResourceBatchResponse(resolveBatchDistributedInternal(identifiers, ResolveType.RESOURCE)));
    }

    public DrirpMessage handleResolveBatchForward(DrirpMessage request) {
        ResolveBatchForwardRequestPayload payload = toBatchRequestPayload(request == null ? null : request.getBody());
        ResolveType resolveType = ResolveType.parse(payload.getResolveType());
        List<String> identifiers = sanitizeIdentifiers(payload.getIdentifiers());
        if (identifiers.isEmpty()) {
            return support.error(request, "VALIDATION_ERROR", "identifiers is required");
        }
        if (identifiers.size() > MAX_BATCH_SIZE) {
            return support.error(request, "VALIDATION_ERROR", "identifiers size must be <= " + MAX_BATCH_SIZE);
        }

        List<String> visitedUrls = support.sanitizeUrls(payload.getVisitedUrls());
        String selfUrl = support.resolveSelfDrirpUrl();
        if (support.contains(visitedUrls, selfUrl)) {
            return support.error(request, "LOOP_DETECTED", "resolve batch loop detected");
        }
        List<String> visitedNext = support.append(visitedUrls, selfUrl);

        String targetCode = extractBatchTargetCode(identifiers);
        if (targetCode == null) {
            return support.error(request, "VALIDATION_ERROR", "identifiers must be valid and target to the same node");
        }
        int ttl = payload.getTtl() == null ? support.normalizeMaxHops() : payload.getTtl().intValue();

        if (support.isLocalTarget(targetCode)) {
            ResolveBatchForwardResponsePayload out = new ResolveBatchForwardResponsePayload();
            out.setResolveType(resolveType.name());
            out.setResolvedBy(support.localNodeCode());
            out.setVisitedUrls(visitedNext);
            out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl)));
            out.setItems(buildLocalBatchPayloadItems(identifiers, resolveType));
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

        if (support.resolveMode() == ResolveMode.ITERATIVE) {
            ResolveBatchForwardResponsePayload out = new ResolveBatchForwardResponsePayload();
            out.setDelegate(Boolean.TRUE);
            out.setResolveType(resolveType.name());
            out.setNextHopCode(support.blankIfNull(hop.getNextHopCode()));
            out.setNextHopDrirpUrl(hop.getNextHopUrl());
            out.setTtlRemaining(Integer.valueOf(Math.max(0, ttl - 1)));
            out.setVisitedUrls(visitedNext);
            return support.ok(request, gson.toJsonTree(out));
        }

        ResolveBatchForwardRequestPayload forwardPayload = new ResolveBatchForwardRequestPayload();
        forwardPayload.setIdentifiers(identifiers);
        forwardPayload.setResolveType(resolveType.name());
        forwardPayload.setTtl(Integer.valueOf(ttl - 1));
        forwardPayload.setVisitedUrls(visitedNext);
        DrirpMessage response = sendBatchForward(hop.getNextHopUrl(), request, forwardPayload);
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

    private <T> DrisResponse<T> validateBatchRequest(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifiers is required", null);
        }
        if (identifiers.size() > MAX_BATCH_SIZE) {
            return DrisResponse.failure("VALIDATION_ERROR", "identifiers size must be <= " + MAX_BATCH_SIZE, null);
        }
        return null;
    }

    private List<BatchResolveEnvelope> resolveBatchDistributedInternal(List<String> identifiers, ResolveType resolveType) {
        List<BatchResolveEnvelope> items = new ArrayList<BatchResolveEnvelope>(identifiers.size());
        Map<String, List<Integer>> groupedIndexes = new LinkedHashMap<String, List<Integer>>();
        for (int i = 0; i < identifiers.size(); i++) {
            String identifier = support.trim(identifiers.get(i));
            BatchResolveEnvelope item = new BatchResolveEnvelope();
            item.identifier = identifier;
            item.targetNodeCode = "";
            items.add(item);

            if (support.isBlank(identifier)) {
                item.payload = buildFailurePayload(identifier, "VALIDATION_ERROR", "identifier is required");
                continue;
            }
            if (!support.isIdentifierValid(identifier)) {
                item.payload = buildFailurePayload(identifier, "VALIDATION_ERROR", "identifier format is invalid");
                continue;
            }
            String targetCode = support.extractTargetCode(identifier);
            item.targetNodeCode = targetCode == null ? "" : targetCode;
            if (targetCode == null) {
                item.payload = buildFailurePayload(identifier, "VALIDATION_ERROR", "identifier format is invalid");
                continue;
            }
            groupedIndexes.computeIfAbsent(targetCode, k -> new ArrayList<Integer>()).add(Integer.valueOf(i));
        }

        List<GroupResolveTask> tasks = new ArrayList<GroupResolveTask>(groupedIndexes.size());
        for (Map.Entry<String, List<Integer>> entry : groupedIndexes.entrySet()) {
            String targetCode = entry.getKey();
            List<Integer> indexes = entry.getValue();
            List<String> groupedIdentifiers = new ArrayList<String>(indexes.size());
            for (Integer index : indexes) {
                groupedIdentifiers.add(items.get(index.intValue()).identifier);
            }
            tasks.add(new GroupResolveTask(
                    targetCode,
                    indexes,
                    groupedIdentifiers,
                    AsyncExecutionSupport.supplyAsync(resolveExecutor, () -> resolveBatchGroupDistributed(groupedIdentifiers, targetCode, resolveType))
            ));
        }
        for (GroupResolveTask task : tasks) {
            List<BatchResolveEnvelope> groupResults = awaitGroupResolve(task, resolveType);
            if (groupResults.size() != task.identifiers.size()) {
                groupResults = buildFailureItems(
                        task.identifiers,
                        task.targetCode,
                        "PROTOCOL_ERROR",
                        "batch resolve result size mismatch"
                );
            }
            for (int i = 0; i < task.indexes.size(); i++) {
                items.set(task.indexes.get(i).intValue(), groupResults.get(i));
            }
        }
        return items;
    }

    private List<BatchResolveEnvelope> resolveBatchGroupDistributed(
            List<String> identifiers,
            String targetCode,
            ResolveType resolveType
    ) {
        if (identifiers == null || identifiers.isEmpty()) {
            return new ArrayList<BatchResolveEnvelope>();
        }
        if (support.isLocalTarget(targetCode)) {
            return resolveBatchGroupLocal(identifiers, targetCode, resolveType);
        }
        if (!properties.isDrirpEnabled()) {
            return buildFailureItems(identifiers, targetCode, "DRIRP_DISABLED", "drirp disabled and target is not local");
        }

        ResolutionForwardHop firstHop = support.resolveNextHop(targetCode);
        if (firstHop == null || support.isBlank(firstHop.getNextHopUrl())) {
            return buildFailureItems(identifiers, targetCode, "ROUTE_NOT_FOUND", "route not found for target code: " + targetCode);
        }
        if (support.resolveMode() == ResolveMode.ITERATIVE) {
            return iterativeResolveBatchGroup(identifiers, targetCode, resolveType, firstHop);
        }
        return recursiveResolveBatchGroup(identifiers, targetCode, resolveType, firstHop);
    }

    private List<BatchResolveEnvelope> resolveBatchGroupLocal(
            List<String> identifiers,
            String targetCode,
            ResolveType resolveType
    ) {
        List<BatchResolveEnvelope> out = new ArrayList<BatchResolveEnvelope>(identifiers.size());
        for (String identifier : identifiers) {
            DrisResponse<?> response = support.resolveLocalRaw(identifier, resolveType);
            out.add(toBatchEnvelope(identifier, targetCode, support.localNodeCode(), response, resolveType));
        }
        return out;
    }

    private List<BatchResolveEnvelope> recursiveResolveBatchGroup(
            List<String> identifiers,
            String targetCode,
            ResolveType resolveType,
            ResolutionForwardHop firstHop
    ) {
        ResolveBatchForwardRequestPayload payload = new ResolveBatchForwardRequestPayload();
        payload.setIdentifiers(identifiers);
        payload.setResolveType(resolveType.name());
        payload.setTtl(Integer.valueOf(support.normalizeMaxHops()));
        payload.setVisitedUrls(support.append(Collections.<String>emptyList(), support.resolveSelfDrirpUrl()));
        DrirpMessage response = sendBatchForward(firstHop.getNextHopUrl(), null, payload);
        return parseBatchResolveResponse(identifiers, targetCode, resolveType, response);
    }

    private List<BatchResolveEnvelope> iterativeResolveBatchGroup(
            List<String> identifiers,
            String targetCode,
            ResolveType resolveType,
            ResolutionForwardHop firstHop
    ) {
        ResolutionForwardHop currentHop = firstHop;
        int ttl = support.normalizeMaxHops();
        List<String> visitedUrls = support.append(Collections.<String>emptyList(), support.resolveSelfDrirpUrl());
        while (ttl > 0) {
            ResolveBatchForwardRequestPayload payload = new ResolveBatchForwardRequestPayload();
            payload.setIdentifiers(identifiers);
            payload.setResolveType(resolveType.name());
            payload.setTtl(Integer.valueOf(ttl));
            payload.setVisitedUrls(visitedUrls);

            DrirpMessage response = sendBatchForward(currentHop.getNextHopUrl(), null, payload);
            if (response == null) {
                return buildFailureItems(identifiers, targetCode, "UPSTREAM_ERROR", "upstream response is null");
            }
            if (!response.isSuccess()) {
                return buildFailureItems(
                        identifiers,
                        targetCode,
                        support.blankIfNull(response.getErrorCode(), "UPSTREAM_ERROR"),
                        support.blankIfNull(response.getErrorMessage(), "upstream error")
                );
            }
            ResolveBatchForwardResponsePayload body = toBatchResponsePayload(response.getBody());
            if (Boolean.TRUE.equals(body.getDelegate())) {
                if (support.isBlank(body.getNextHopDrirpUrl())) {
                    return buildFailureItems(identifiers, targetCode, "PROTOCOL_ERROR", "delegate response missing next hop url");
                }
                currentHop = new ResolutionForwardHop(body.getNextHopCode(), support.trim(body.getNextHopDrirpUrl()));
                visitedUrls = support.sanitizeUrls(body.getVisitedUrls());
                ttl = body.getTtlRemaining() == null ? ttl - 1 : body.getTtlRemaining().intValue();
                continue;
            }
            return mapBatchItems(identifiers, targetCode, resolveType, body);
        }
        return buildFailureItems(identifiers, targetCode, "TTL_EXCEEDED", "resolve ttl exceeded");
    }

    private List<BatchResolveEnvelope> awaitGroupResolve(GroupResolveTask task, ResolveType resolveType) {
        try {
            return AsyncExecutionSupport.await(task.future, "batch resolve group");
        } catch (RuntimeException exception) {
            return buildFailureItems(
                    task.identifiers,
                    task.targetCode,
                    "INTERNAL_ERROR",
                    support.blankIfNull(exception.getMessage(), "batch resolve failed")
            );
        }
    }

    private List<BatchResolveEnvelope> parseBatchResolveResponse(
            List<String> identifiers,
            String targetCode,
            ResolveType resolveType,
            DrirpMessage response
    ) {
        if (response == null) {
            return buildFailureItems(identifiers, targetCode, "UPSTREAM_ERROR", "upstream response is null");
        }
        if (!response.isSuccess()) {
            return buildFailureItems(
                    identifiers,
                    targetCode,
                    support.blankIfNull(response.getErrorCode(), "UPSTREAM_ERROR"),
                    support.blankIfNull(response.getErrorMessage(), "upstream error")
            );
        }
        return mapBatchItems(identifiers, targetCode, resolveType, toBatchResponsePayload(response.getBody()));
    }

    private List<BatchResolveEnvelope> mapBatchItems(
            List<String> identifiers,
            String targetCode,
            ResolveType resolveType,
            ResolveBatchForwardResponsePayload payload
    ) {
        if (payload != null
                && !support.isBlank(payload.getResolveType())
                && ResolveType.parse(payload.getResolveType()) != resolveType) {
            return buildFailureItems(identifiers, targetCode, "PROTOCOL_ERROR", "batch resolve type mismatch");
        }
        List<ResolveBatchItemPayload> payloadItems = payload == null ? null : payload.getItems();
        if (payloadItems == null || payloadItems.size() != identifiers.size()) {
            return buildFailureItems(identifiers, targetCode, "PROTOCOL_ERROR", "batch resolve response is invalid");
        }
        List<BatchResolveEnvelope> out = new ArrayList<BatchResolveEnvelope>(identifiers.size());
        String resolvedBy = payload == null ? "" : support.blankIfNull(payload.getResolvedBy());
        for (int i = 0; i < identifiers.size(); i++) {
            out.add(toBatchEnvelope(identifiers.get(i), targetCode, resolvedBy, payloadItems.get(i), resolveType));
        }
        return out;
    }

    private List<ResolveBatchItemPayload> buildLocalBatchPayloadItems(List<String> identifiers, ResolveType resolveType) {
        List<ResolveBatchItemPayload> out = new ArrayList<ResolveBatchItemPayload>(identifiers.size());
        for (String identifier : identifiers) {
            out.add(toBatchPayload(identifier, support.resolveLocalRaw(identifier, resolveType), resolveType));
        }
        return out;
    }

    private BatchResolveEnvelope toBatchEnvelope(
            String identifier,
            String targetCode,
            String resolvedByNodeCode,
            DrisResponse<?> response,
            ResolveType resolveType
    ) {
        BatchResolveEnvelope item = new BatchResolveEnvelope();
        item.identifier = identifier;
        item.targetNodeCode = targetCode;
        item.resolvedByNodeCode = resolvedByNodeCode;
        item.payload = toBatchPayload(identifier, response, resolveType);
        return item;
    }

    private BatchResolveEnvelope toBatchEnvelope(
            String identifier,
            String targetCode,
            String resolvedByNodeCode,
            ResolveBatchItemPayload payload,
            ResolveType resolveType
    ) {
        BatchResolveEnvelope item = new BatchResolveEnvelope();
        item.identifier = identifier;
        item.targetNodeCode = targetCode;
        item.resolvedByNodeCode = resolvedByNodeCode;
        item.payload = normalizeBatchPayload(identifier, payload, resolveType);
        return item;
    }

    private ResolveBatchItemPayload toBatchPayload(
            String identifier,
            DrisResponse<?> response,
            ResolveType resolveType
    ) {
        ResolveBatchItemPayload payload = new ResolveBatchItemPayload();
        payload.setIdentifier(identifier);
        Object resolvedData = response == null ? null : castResolvedData(resolveType, response.getData());
        boolean success = response != null && response.isOk() && resolvedData != null;
        payload.setSuccess(Boolean.valueOf(success));
        if (success) {
            payload.setCode("OK");
            payload.setMessage("ok");
            applyResolvedData(payload, resolveType, resolvedData);
            return payload;
        }
        payload.setCode(response == null ? "UPSTREAM_ERROR" : support.blankIfNull(response.getCode(), "UPSTREAM_ERROR"));
        payload.setMessage(response == null ? "resolve failed" : support.blankIfNull(response.getMessage(), "resolve failed"));
        return payload;
    }

    private ResolveBatchItemPayload normalizeBatchPayload(
            String identifier,
            ResolveBatchItemPayload source,
            ResolveType resolveType
    ) {
        if (source == null) {
            return buildFailurePayload(identifier, "PROTOCOL_ERROR", "batch resolve item is missing");
        }
        Object resolvedData = castResolvedData(resolveType, extractResolvedData(source, resolveType));
        if (Boolean.TRUE.equals(source.getSuccess())) {
            if (resolvedData == null) {
                return buildFailurePayload(identifier, "PROTOCOL_ERROR", "resolved data is missing");
            }
            ResolveBatchItemPayload payload = new ResolveBatchItemPayload();
            payload.setIdentifier(identifier);
            payload.setSuccess(Boolean.TRUE);
            payload.setCode("OK");
            payload.setMessage(support.blankIfNull(source.getMessage(), "ok"));
            applyResolvedData(payload, resolveType, resolvedData);
            return payload;
        }
        return buildFailurePayload(
                identifier,
                support.blankIfNull(source.getCode(), "ERROR"),
                support.blankIfNull(source.getMessage(), "resolve failed")
        );
    }

    private List<BatchResolveEnvelope> buildFailureItems(
            List<String> identifiers,
            String targetCode,
            String code,
            String message
    ) {
        List<BatchResolveEnvelope> out = new ArrayList<BatchResolveEnvelope>(identifiers.size());
        for (String identifier : identifiers) {
            BatchResolveEnvelope item = new BatchResolveEnvelope();
            item.identifier = identifier;
            item.targetNodeCode = targetCode;
            item.resolvedByNodeCode = "";
            item.payload = buildFailurePayload(identifier, code, message);
            out.add(item);
        }
        return out;
    }

    private ResolveBatchItemPayload buildFailurePayload(String identifier, String code, String message) {
        ResolveBatchItemPayload payload = new ResolveBatchItemPayload();
        payload.setIdentifier(identifier);
        payload.setSuccess(Boolean.FALSE);
        payload.setCode(code);
        payload.setMessage(message);
        return payload;
    }

    private BatchResolveMetadataResponse toMetadataBatchResponse(List<BatchResolveEnvelope> items) {
        BatchResolveMetadataResponse out = new BatchResolveMetadataResponse();
        out.setMode(resolveBatchMode());
        out.setTotal(items.size());
        out.setSuccessCount(countSuccess(items));
        out.setFailureCount(items.size() - out.getSuccessCount());
        out.setPartial(out.getFailureCount() > 0);

        List<BatchResolveMetadataItem> mapped = new ArrayList<BatchResolveMetadataItem>(items.size());
        for (BatchResolveEnvelope item : items) {
            mapped.add(toMetadataItem(item));
        }
        out.setItems(mapped);
        return out;
    }

    private BatchResolveDigitalObjectResponse toDigitalObjectBatchResponse(List<BatchResolveEnvelope> items) {
        BatchResolveDigitalObjectResponse out = new BatchResolveDigitalObjectResponse();
        out.setMode(resolveBatchMode());
        out.setTotal(items.size());
        out.setSuccessCount(countSuccess(items));
        out.setFailureCount(items.size() - out.getSuccessCount());
        out.setPartial(out.getFailureCount() > 0);

        List<BatchResolveDigitalObjectItem> mapped = new ArrayList<BatchResolveDigitalObjectItem>(items.size());
        for (BatchResolveEnvelope item : items) {
            mapped.add(toDigitalObjectItem(item));
        }
        out.setItems(mapped);
        return out;
    }

    private BatchResolveResourceResponse toResourceBatchResponse(List<BatchResolveEnvelope> items) {
        BatchResolveResourceResponse out = new BatchResolveResourceResponse();
        out.setMode(resolveBatchMode());
        out.setTotal(items.size());
        out.setSuccessCount(countSuccess(items));
        out.setFailureCount(items.size() - out.getSuccessCount());
        out.setPartial(out.getFailureCount() > 0);

        List<BatchResolveResourceItem> mapped = new ArrayList<BatchResolveResourceItem>(items.size());
        for (BatchResolveEnvelope item : items) {
            mapped.add(toResourceItem(item));
        }
        out.setItems(mapped);
        return out;
    }

    private BatchResolveMetadataItem toMetadataItem(BatchResolveEnvelope envelope) {
        BatchResolveMetadataItem item = new BatchResolveMetadataItem();
        item.setIdentifier(envelope.identifier);
        item.setTargetNodeCode(support.blankIfNull(envelope.targetNodeCode));
        item.setResolvedByNodeCode(support.blankIfNull(envelope.resolvedByNodeCode));
        ResolveBatchItemPayload payload = envelope.payload == null
                ? buildFailurePayload(envelope.identifier, "PROTOCOL_ERROR", "batch resolve item is missing")
                : envelope.payload;
        item.setSuccess(Boolean.TRUE.equals(payload.getSuccess()));
        item.setCode(support.blankIfNull(payload.getCode(), "ERROR"));
        item.setMessage(support.blankIfNull(payload.getMessage(), "resolve failed"));
        item.setMetadata(item.isSuccess() ? payload.getMetadata() : null);
        return item;
    }

    private BatchResolveDigitalObjectItem toDigitalObjectItem(BatchResolveEnvelope envelope) {
        BatchResolveDigitalObjectItem item = new BatchResolveDigitalObjectItem();
        item.setIdentifier(envelope.identifier);
        item.setTargetNodeCode(support.blankIfNull(envelope.targetNodeCode));
        item.setResolvedByNodeCode(support.blankIfNull(envelope.resolvedByNodeCode));
        ResolveBatchItemPayload payload = envelope.payload == null
                ? buildFailurePayload(envelope.identifier, "PROTOCOL_ERROR", "batch resolve item is missing")
                : envelope.payload;
        item.setSuccess(Boolean.TRUE.equals(payload.getSuccess()));
        item.setCode(support.blankIfNull(payload.getCode(), "ERROR"));
        item.setMessage(support.blankIfNull(payload.getMessage(), "resolve failed"));
        item.setDigitalObject(item.isSuccess() ? payload.getDigitalObject() : null);
        return item;
    }

    private BatchResolveResourceItem toResourceItem(BatchResolveEnvelope envelope) {
        BatchResolveResourceItem item = new BatchResolveResourceItem();
        item.setIdentifier(envelope.identifier);
        item.setTargetNodeCode(support.blankIfNull(envelope.targetNodeCode));
        item.setResolvedByNodeCode(support.blankIfNull(envelope.resolvedByNodeCode));
        ResolveBatchItemPayload payload = envelope.payload == null
                ? buildFailurePayload(envelope.identifier, "PROTOCOL_ERROR", "batch resolve item is missing")
                : envelope.payload;
        item.setSuccess(Boolean.TRUE.equals(payload.getSuccess()));
        item.setCode(support.blankIfNull(payload.getCode(), "ERROR"));
        item.setMessage(support.blankIfNull(payload.getMessage(), "resolve failed"));
        item.setResource(item.isSuccess() ? payload.getResource() : null);
        return item;
    }

    private Object castResolvedData(ResolveType resolveType, Object source) {
        if (resolveType == ResolveType.DIGITAL_OBJECT) {
            return support.castData(source, DigitalObject.class);
        }
        if (resolveType == ResolveType.RESOURCE) {
            return support.castData(source, Resource.class);
        }
        return support.castData(source, IdentifierMetadata.class);
    }

    private Object extractResolvedData(ResolveBatchItemPayload payload, ResolveType resolveType) {
        if (payload == null) {
            return null;
        }
        if (resolveType == ResolveType.DIGITAL_OBJECT) {
            return payload.getDigitalObject();
        }
        if (resolveType == ResolveType.RESOURCE) {
            return payload.getResource();
        }
        return payload.getMetadata();
    }

    private void applyResolvedData(ResolveBatchItemPayload payload, ResolveType resolveType, Object data) {
        if (resolveType == ResolveType.DIGITAL_OBJECT) {
            payload.setDigitalObject((DigitalObject) data);
            return;
        }
        if (resolveType == ResolveType.RESOURCE) {
            payload.setResource((Resource) data);
            return;
        }
        payload.setMetadata((IdentifierMetadata) data);
    }

    private String resolveBatchMode() {
        return properties.isDrirpEnabled() ? support.resolveMode().name().toLowerCase() : "local";
    }

    private int countSuccess(List<BatchResolveEnvelope> items) {
        int successCount = 0;
        for (BatchResolveEnvelope item : items) {
            if (item != null && item.payload != null && Boolean.TRUE.equals(item.payload.getSuccess())) {
                successCount++;
            }
        }
        return successCount;
    }

    private DrirpMessage sendBatchForward(
            String nextHopUrl,
            DrirpMessage sourceRequest,
            ResolveBatchForwardRequestPayload payload
    ) {
        long startedAt = System.currentTimeMillis();
        DrirpClientImpl client = null;
        String effectiveNextHopUrl = support.trim(nextHopUrl);
        String traceId = sourceRequest == null ? null : sourceRequest.getTraceId();
        boolean success = false;
        int status = 502;
        String errorMessage = "";
        try {
            client = support.acquireForwardClient(effectiveNextHopUrl);
            DrirpMessage forward = new DrirpMessage(NodeDrirpOpcode.RESOLVE_BATCH_FORWARD, gson.toJsonTree(payload));
            if (sourceRequest != null) {
                forward.setRid(sourceRequest.getRid());
                forward.setTraceId(sourceRequest.getTraceId());
                forward.setVersion(sourceRequest.getVersion());
            }
            DrirpMessage response = client.send(forward);
            traceId = support.isBlank(traceId) ? (response == null ? forward.getTraceId() : response.getTraceId()) : traceId;
            success = response != null && response.isSuccess();
            status = success ? 200 : 502;
            if (!success) {
                errorMessage = response == null
                        ? "upstream response is null"
                        : support.blankIfNull(response.getErrorMessage(), support.blankIfNull(response.getErrorCode(), "upstream error"));
            }
            return response;
        } catch (DrirpClientException e) {
            support.invalidateForwardClient(effectiveNextHopUrl, client);
            errorMessage = support.blankIfNull(e.getMessage(), "drirp batch forward failed");
            DrirpMessage error = DrirpMessage.error(
                    NodeDrirpOpcode.RESOLVE_BATCH_FORWARD,
                    sourceRequest == null ? null : sourceRequest.getRid(),
                    "drirp batch forward failed: " + e.getMessage()
            );
            error.setErrorCode("UPSTREAM_ERROR");
            error.setOk(Boolean.FALSE);
            return error;
        } finally {
            support.recordOutboundAudit(
                    "RESOLVE_BATCH_FORWARD_OUT",
                    traceId,
                    effectiveNextHopUrl,
                    status,
                    System.currentTimeMillis() - startedAt,
                    success,
                    errorMessage
            );
        }
    }

    private ResolveBatchForwardRequestPayload toBatchRequestPayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new ResolveBatchForwardRequestPayload();
        }
        try {
            ResolveBatchForwardRequestPayload payload = gson.fromJson(body, ResolveBatchForwardRequestPayload.class);
            return payload == null ? new ResolveBatchForwardRequestPayload() : payload;
        } catch (Exception ignored) {
            return new ResolveBatchForwardRequestPayload();
        }
    }

    private ResolveBatchForwardResponsePayload toBatchResponsePayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new ResolveBatchForwardResponsePayload();
        }
        try {
            ResolveBatchForwardResponsePayload payload = gson.fromJson(body, ResolveBatchForwardResponsePayload.class);
            return payload == null ? new ResolveBatchForwardResponsePayload() : payload;
        } catch (Exception ignored) {
            return new ResolveBatchForwardResponsePayload();
        }
    }

    private List<String> sanitizeIdentifiers(List<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> out = new ArrayList<String>(identifiers.size());
        for (String identifier : identifiers) {
            String value = support.trim(identifier);
            if (!support.isBlank(value)) {
                out.add(value);
            }
        }
        return out;
    }

    private String extractBatchTargetCode(List<String> identifiers) {
        String targetCode = null;
        for (String identifier : identifiers) {
            if (!support.isIdentifierValid(identifier)) {
                return null;
            }
            String currentTargetCode = support.extractTargetCode(identifier);
            if (currentTargetCode == null) {
                return null;
            }
            if (targetCode == null) {
                targetCode = currentTargetCode;
                continue;
            }
            if (!targetCode.equals(currentTargetCode)) {
                return null;
            }
        }
        return targetCode;
    }

    private static final class GroupResolveTask {
        private final String targetCode;
        private final List<Integer> indexes;
        private final List<String> identifiers;
        private final CompletableFuture<List<BatchResolveEnvelope>> future;

        private GroupResolveTask(
                String targetCode,
                List<Integer> indexes,
                List<String> identifiers,
                CompletableFuture<List<BatchResolveEnvelope>> future
        ) {
            this.targetCode = targetCode;
            this.indexes = indexes;
            this.identifiers = identifiers;
            this.future = future;
        }
    }

    private static class BatchResolveEnvelope {
        private String identifier;
        private String targetNodeCode;
        private String resolvedByNodeCode;
        private ResolveBatchItemPayload payload;
    }
}

