package com.dris.v2.backend.service.operation;

import com.dris.v2.backend.dto.ToolExecuteRequest;
import com.dris.v2.backend.dto.ToolExecuteResult;
import com.dris.v2.backend.model.DirectDescendantRecord;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.model.OperationForwardRequestPayload;
import com.dris.v2.backend.model.OperationForwardResponsePayload;
import com.dris.v2.backend.model.OperationType;
import com.dris.v2.backend.model.RouteForwardRecord;
import com.dris.v2.backend.common.drirp.SharedDrirpClientPool;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.risk.RiskDecisionResult;
import com.dris.v2.backend.risk.RiskDecisionService;
import com.dris.v2.backend.service.DrirpAuditService;
import com.dris.v2.backend.service.ToolExecutionLocalService;
import com.dris.v2.backend.service.resolve.ResolveMode;
import com.dris.v2.backend.service.local.upload.LocalUploadService;
import com.dris.v2.backend.contract.upload.DownloadPresignRequest;
import com.dris.v2.backend.contract.upload.DownloadPresignResult;
import com.dris.v2.backend.contract.DrisResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bdware.drirp.client.DrirpClientBuilder;
import org.bdware.drirp.client.DrirpClientImpl;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class OperationSupport {
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[0-9]{4}[A-HJ-NP-Z]{4}[0-9]{2}[1239][123][0-9]{8}[0-9A-HJ-NP-Z]{10}$");

    private final LocalUploadService localUploadService;
    private final ToolExecutionLocalService toolExecutionLocalService;
    private final BackendV2Properties properties;
    private final RouteForwardDao routeForwardDao;
    private final DirectDescendantDao directDescendantDao;
    private final RiskDecisionService riskDecisionService;
    private final DrirpSecuritySupport drirpSecuritySupport;
    private final DrirpAuditService drirpAuditService;
    private final Gson gson = new Gson();

    public OperationSupport(
            LocalUploadService localUploadService,
            ToolExecutionLocalService toolExecutionLocalService,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            RiskDecisionService riskDecisionService,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService
    ) {
        this.localUploadService = localUploadService;
        this.toolExecutionLocalService = toolExecutionLocalService;
        this.properties = properties;
        this.routeForwardDao = routeForwardDao;
        this.directDescendantDao = directDescendantDao;
        this.riskDecisionService = riskDecisionService;
        this.drirpSecuritySupport = drirpSecuritySupport;
        this.drirpAuditService = drirpAuditService;
    }

    public ResolveMode resolveMode() {
        return ResolveMode.parse(properties.getResolveMode());
    }

    public boolean isIdentifierValid(String identifier) {
        String value = trim(identifier);
        return !isBlank(value) && IDENTIFIER_PATTERN.matcher(value).matches();
    }

    public DrisResponse<ToolExecuteResult> executeToolLocal(String userId, String identifier, ToolExecuteRequest request) {
        RiskDecisionResult decision = riskDecisionService.evaluateToolExecution(userId, identifier, request);
        if (decision == null || !decision.isAllowed()) {
            return DrisResponse.failure(
                    "FORBIDDEN",
                    blankIfNull(decision == null ? null : decision.getReason(), "tool execution denied"),
                    null
            );
        }
        DrisResponse<ToolExecuteResult> local = toolExecutionLocalService.executeToolLocal(identifier, request);
        return attachHandledBy(local, localNodeCode());
    }

    public DrisResponse<DownloadPresignResult> createDownloadPresignLocal(
            String userId,
            String identifier,
            DownloadPresignRequest request
    ) {
        DownloadPresignRequest localRequest = normalizePresignRequest(request);
        if (localRequest == null) {
            return DrisResponse.failure("VALIDATION_ERROR", "downloadPresignRequest is required", null);
        }
        localRequest.setIdentifier(identifier);
        RiskDecisionResult decision = riskDecisionService.evaluateDownload(userId, identifier, localRequest);
        if (decision == null || !decision.isAllowed()) {
            return DrisResponse.failure(
                    "FORBIDDEN",
                    blankIfNull(decision == null ? null : decision.getReason(), "download denied"),
                    null
            );
        }
        return localUploadService.createDownloadPresign(localRequest);
    }

    public OperationForwardRequestPayload buildToolExecutePayload(
            String identifier,
            String userId,
            int ttl,
            List<String> visitedUrls,
            ToolExecuteRequest request
    ) {
        OperationForwardRequestPayload payload = new OperationForwardRequestPayload();
        payload.setIdentifier(identifier);
        payload.setOperationType(OperationType.TOOL_EXECUTE.name());
        payload.setTtl(Integer.valueOf(ttl));
        payload.setUserId(userId);
        payload.setVisitedUrls(visitedUrls);
        payload.setToolExecuteRequest(request);
        return payload;
    }

    public OperationForwardRequestPayload buildDownloadPresignPayload(
            String identifier,
            String userId,
            int ttl,
            List<String> visitedUrls,
            DownloadPresignRequest request
    ) {
        OperationForwardRequestPayload payload = new OperationForwardRequestPayload();
        payload.setIdentifier(identifier);
        payload.setOperationType(OperationType.DOWNLOAD_PRESIGN.name());
        payload.setTtl(Integer.valueOf(ttl));
        payload.setUserId(userId);
        payload.setVisitedUrls(visitedUrls);
        payload.setDownloadPresignRequest(request);
        return payload;
    }

    public DrirpMessage sendForward(String nextHopUrl, DrirpMessage sourceRequest, OperationForwardRequestPayload payload)
            throws DrirpClientException {
        long startedAt = System.currentTimeMillis();
        DrirpClientImpl client = null;
        String effectiveNextHopUrl = trim(nextHopUrl);
        long timeoutMs = normalizeTimeoutMs();
        DrirpMessage request = null;
        String traceId = sourceRequest == null ? null : sourceRequest.getTraceId();
        String userId = payload == null ? "" : trim(payload.getUserId());
        boolean success = false;
        int status = 502;
        String errorMessage = "";
        try {
            client = SharedDrirpClientPool.acquire(effectiveNextHopUrl, timeoutMs, drirpSecuritySupport);
            request = new DrirpMessage(NodeDrirpOpcode.OP_FORWARD, gson.toJsonTree(payload));
            request.setTs(Long.valueOf(System.currentTimeMillis()));
            request.setRid(String.valueOf(System.nanoTime()));
            request.setVersion("1.0");
            if (sourceRequest != null) {
                request.setTraceId(sourceRequest.getTraceId());
            }
            DrirpMessage response = client.send(request);
            traceId = isBlank(traceId) ? (response == null ? request.getTraceId() : response.getTraceId()) : traceId;
            success = response != null && response.isSuccess();
            status = success ? 200 : 502;
            if (!success) {
                errorMessage = response == null
                        ? "upstream response is null"
                        : blankIfNull(response.getErrorMessage(), blankIfNull(response.getErrorCode(), "upstream error"));
            }
            return response;
        } catch (DrirpClientException ex) {
            SharedDrirpClientPool.invalidate(effectiveNextHopUrl, timeoutMs, client);
            errorMessage = blankIfNull(ex.getMessage(), "drirp client exception");
            throw ex;
        } finally {
            recordOutboundAudit(traceId, userId, effectiveNextHopUrl, status, System.currentTimeMillis() - startedAt, success, errorMessage);
        }
    }

    public DrisResponse<ToolExecuteResult> parseToolExecuteResponse(DrirpMessage response) {
        if (response == null) {
            return DrisResponse.failure("UPSTREAM_ERROR", "upstream response is null", null);
        }
        if (!response.isSuccess()) {
            DrisResponse<ToolExecuteResult> fail = DrisResponse.failure(
                    blankIfNull(response.getErrorCode(), "UPSTREAM_ERROR"),
                    blankIfNull(response.getErrorMessage(), "upstream error"),
                    response.getDetails()
            );
            fail.setTraceId(response.getTraceId());
            if (response.getTs() != null && response.getTs().longValue() > 0L) {
                fail.setTimestamp(Instant.ofEpochMilli(response.getTs().longValue()).toString());
            }
            return fail;
        }
        OperationForwardResponsePayload payload = toResponsePayload(response.getBody());
        ToolExecuteResult result = payload.getToolExecuteResult();
        if (result == null) {
            JsonObject body = toJsonObject(response.getBody());
            if (body != null && body.has("toolExecuteResult") && !body.get("toolExecuteResult").isJsonNull()) {
                result = gson.fromJson(body.get("toolExecuteResult"), ToolExecuteResult.class);
            }
        }
        if (result == null) {
            return DrisResponse.failure("PROTOCOL_ERROR", "upstream tool result is empty", null);
        }
        if (isBlank(result.getHandledByNodeCode())) {
            result.setHandledByNodeCode(blankIfNull(payload.getHandledBy()));
        }
        DrisResponse<ToolExecuteResult> success = DrisResponse.success(result);
        success.setTraceId(response.getTraceId());
        if (response.getTs() != null && response.getTs().longValue() > 0L) {
            success.setTimestamp(Instant.ofEpochMilli(response.getTs().longValue()).toString());
        }
        return success;
    }

    public DrisResponse<DownloadPresignResult> parseDownloadPresignResponse(DrirpMessage response) {
        if (response == null) {
            return DrisResponse.failure("UPSTREAM_ERROR", "upstream response is null", null);
        }
        if (!response.isSuccess()) {
            DrisResponse<DownloadPresignResult> fail = DrisResponse.failure(
                    blankIfNull(response.getErrorCode(), "UPSTREAM_ERROR"),
                    blankIfNull(response.getErrorMessage(), "upstream error"),
                    response.getDetails()
            );
            fail.setTraceId(response.getTraceId());
            if (response.getTs() != null && response.getTs().longValue() > 0L) {
                fail.setTimestamp(Instant.ofEpochMilli(response.getTs().longValue()).toString());
            }
            return fail;
        }
        OperationForwardResponsePayload payload = toResponsePayload(response.getBody());
        DownloadPresignResult result = payload.getDownloadPresignResult();
        if (result == null) {
            JsonObject body = toJsonObject(response.getBody());
            if (body != null && body.has("downloadPresignResult") && !body.get("downloadPresignResult").isJsonNull()) {
                result = gson.fromJson(body.get("downloadPresignResult"), DownloadPresignResult.class);
            }
        }
        if (result == null) {
            return DrisResponse.failure("PROTOCOL_ERROR", "upstream download presign result is empty", null);
        }
        DrisResponse<DownloadPresignResult> success = DrisResponse.success(result);
        success.setTraceId(response.getTraceId());
        if (response.getTs() != null && response.getTs().longValue() > 0L) {
            success.setTimestamp(Instant.ofEpochMilli(response.getTs().longValue()).toString());
        }
        return success;
    }

    public DownloadPresignRequest normalizePresignRequest(DownloadPresignRequest request) {
        if (request == null) {
            return null;
        }
        DownloadPresignRequest normalized = new DownloadPresignRequest();
        normalized.setIdentifier(trim(request.getIdentifier()));
        normalized.setExpiresInSeconds(request.getExpiresInSeconds());
        return normalized;
    }

    public OperationForwardRequestPayload toRequestPayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new OperationForwardRequestPayload();
        }
        try {
            OperationForwardRequestPayload payload = gson.fromJson(body, OperationForwardRequestPayload.class);
            return payload == null ? new OperationForwardRequestPayload() : payload;
        } catch (Exception ignored) {
            return new OperationForwardRequestPayload();
        }
    }

    public OperationForwardResponsePayload toResponsePayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new OperationForwardResponsePayload();
        }
        try {
            OperationForwardResponsePayload payload = gson.fromJson(body, OperationForwardResponsePayload.class);
            return payload == null ? new OperationForwardResponsePayload() : payload;
        } catch (Exception ignored) {
            return new OperationForwardResponsePayload();
        }
    }

    public DrirpClientBuilder createClientBuilder(String url) {
        DrirpClientBuilder builder = new DrirpClientBuilder()
                .url(url)
                .timeoutMs(normalizeTimeoutMs());
        return drirpSecuritySupport.applyClientSecurity(builder);
    }

    public void recordOutboundAudit(
            String traceId,
            String userId,
            String path,
            int status,
            long durationMs,
            boolean success,
            String errorMessage
    ) {
        drirpAuditService.record("OP_FORWARD_OUT", traceId, userId, path, status, durationMs, success, errorMessage);
    }

    public OperationForwardHop resolveNextHop(String targetCode) {
        if (isBlank(targetCode)) {
            return null;
        }
        RouteForwardRecord route = routeForwardDao.findByTargetCode(targetCode);
        if (route != null && !isBlank(route.getNextHopDrirpUrl())) {
            return new OperationForwardHop(route.getNextHopCode(), trim(route.getNextHopDrirpUrl()));
        }
        DirectDescendantRecord direct = directDescendantDao.findByChildCode(targetCode);
        if (direct != null
                && "ACTIVE".equalsIgnoreCase(direct.getRelationStatus())
                && !isBlank(direct.getChildDrirpUrl())) {
            return new OperationForwardHop(targetCode, trim(direct.getChildDrirpUrl()));
        }
        if (properties.isResolveAllowParentFallback() && !isBlank(properties.getParentDrirpUrl())) {
            return new OperationForwardHop(resolveParentNodeCode(), trim(properties.getParentDrirpUrl()));
        }
        return null;
    }

    public boolean isLocalTarget(String targetCode) {
        return localNodeCode().equals(targetCode);
    }

    public String localNodeCode() {
        return trim(properties.getRegionCode()) + trim(properties.getOrganizationCode());
    }

    public String extractTargetCode(String identifier) {
        String id = trim(identifier);
        if (id.length() < 8) {
            return null;
        }
        return id.substring(0, 8);
    }

    public String resolveSelfDrirpUrl() {
        if (!isBlank(properties.getDrirpPublicUrl())) {
            return trim(properties.getDrirpPublicUrl());
        }
        return "grpc://127.0.0.1:" + properties.getDrirpPort();
    }

    public int normalizeMaxHops() {
        return properties.getResolveMaxHops() <= 0 ? 8 : properties.getResolveMaxHops();
    }

    public long normalizeTimeoutMs() {
        return properties.getResolveTimeoutMs() <= 0 ? 5000L : properties.getResolveTimeoutMs();
    }

    public List<String> sanitizeUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> out = new ArrayList<String>();
        for (String url : urls) {
            if (!isBlank(url)) {
                out.add(url.trim());
            }
        }
        return out;
    }

    public List<String> append(List<String> source, String value) {
        List<String> out = new ArrayList<String>();
        if (source != null) {
            out.addAll(source);
        }
        if (!isBlank(value)) {
            out.add(value);
        }
        return out;
    }

    public boolean contains(List<String> values, String value) {
        if (values == null || isBlank(value)) {
            return false;
        }
        for (String item : values) {
            if (value.equals(item)) {
                return true;
            }
        }
        return false;
    }

    public JsonElement toJsonTree(Object source) {
        return gson.toJsonTree(source);
    }

    public DrirpMessage ok(DrirpMessage request, JsonElement body) {
        DrirpMessage response = DrirpMessage.ok(
                request == null ? NodeDrirpOpcode.OP_FORWARD : request.getCode(),
                request == null ? null : request.getRid(),
                body
        );
        response.setOk(Boolean.TRUE);
        if (request != null) {
            response.setTraceId(request.getTraceId());
            response.setVersion(request.getVersion());
        }
        return response;
    }

    public DrirpMessage error(DrirpMessage request, String errorCode, String message) {
        DrirpMessage response = DrirpMessage.error(
                request == null ? NodeDrirpOpcode.OP_FORWARD : request.getCode(),
                request == null ? null : request.getRid(),
                message
        );
        response.setErrorCode(blankIfNull(errorCode, "ERROR"));
        response.setErrorMessage(blankIfNull(message, "error"));
        response.setOk(Boolean.FALSE);
        if (request != null) {
            response.setTraceId(request.getTraceId());
            response.setVersion(request.getVersion());
        }
        return response;
    }

    public String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    public String blankIfNull(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    public String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveParentNodeCode() {
        String parentRegionCode = trim(properties.getParentRegionCode());
        String parentOrganizationCode = trim(properties.getParentOrganizationCode());
        if (parentRegionCode.length() == 4 && parentOrganizationCode.length() == 4) {
            return parentRegionCode + parentOrganizationCode;
        }
        return "";
    }

    private DrisResponse<ToolExecuteResult> attachHandledBy(DrisResponse<ToolExecuteResult> response, String handledByNodeCode) {
        if (response == null || !response.isOk() || response.getData() == null) {
            return response;
        }
        response.getData().setHandledByNodeCode(handledByNodeCode);
        return response;
    }

    private JsonObject toJsonObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }
}
