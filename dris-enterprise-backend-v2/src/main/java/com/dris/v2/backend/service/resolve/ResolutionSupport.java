package com.dris.v2.backend.service.resolve;

import com.dris.v2.backend.sdknext.BackendKernelResponseSupport;
import com.dris.v2.backend.sdknext.LocalDrisKernelFacade;
import com.dris.sdk.next.domain.DigitalObject;
import com.dris.sdk.next.domain.IdentifierMetadata;
import com.dris.sdk.next.domain.Resource;
import com.dris.v2.backend.common.drirp.SharedDrirpClientPool;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.model.DirectDescendantRecord;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.model.ResolveForwardRequestPayload;
import com.dris.v2.backend.model.ResolveForwardResponsePayload;
import com.dris.v2.backend.model.ResolveType;
import com.dris.v2.backend.model.RouteForwardRecord;
import com.dris.v2.backend.service.DrirpAuditService;
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

public class ResolutionSupport {
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("^[0-9]{4}[A-HJ-NP-Z]{4}[0-9]{2}[1239][123][0-9]{8}[0-9A-HJ-NP-Z]{10}$");

    private final LocalDrisKernelFacade localDrisKernelFacade;
    private final BackendV2Properties properties;
    private final RouteForwardDao routeForwardDao;
    private final DirectDescendantDao directDescendantDao;
    private final DrirpSecuritySupport drirpSecuritySupport;
    private final DrirpAuditService drirpAuditService;
    private final Gson gson = new Gson();

    public ResolutionSupport(
            LocalDrisKernelFacade localDrisKernelFacade,
            BackendV2Properties properties,
            RouteForwardDao routeForwardDao,
            DirectDescendantDao directDescendantDao,
            DrirpSecuritySupport drirpSecuritySupport,
            DrirpAuditService drirpAuditService
    ) {
        this.localDrisKernelFacade = localDrisKernelFacade;
        this.properties = properties;
        this.routeForwardDao = routeForwardDao;
        this.directDescendantDao = directDescendantDao;
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

    public <T> DrisResponse<T> resolveLocal(String identifier, ResolveType resolveType, Class<T> dataType) {
        DrisResponse<?> local = resolveLocalRaw(identifier, resolveType);
        return mapResponse(local, dataType);
    }

    public DrisResponse<?> resolveLocalRaw(String identifier, ResolveType resolveType) {
        try {
            if (resolveType == ResolveType.DIGITAL_OBJECT) {
                return BackendKernelResponseSupport.success(localDrisKernelFacade.getDigitalObject(identifier));
            }
            if (resolveType == ResolveType.RESOURCE) {
                return BackendKernelResponseSupport.success(localDrisKernelFacade.getResource(identifier));
            }
            return BackendKernelResponseSupport.success(localDrisKernelFacade.getMetadata(identifier));
        } catch (RuntimeException exception) {
            return BackendKernelResponseSupport.failure(exception);
        }
    }

    public ResolutionForwardHop resolveNextHop(String targetCode) {
        if (isBlank(targetCode)) {
            return null;
        }
        RouteForwardRecord route = routeForwardDao.findByTargetCode(targetCode);
        if (route != null && !isBlank(route.getNextHopDrirpUrl())) {
            return new ResolutionForwardHop(route.getNextHopCode(), trim(route.getNextHopDrirpUrl()));
        }
        DirectDescendantRecord direct = directDescendantDao.findByChildCode(targetCode);
        if (direct != null
                && "ACTIVE".equalsIgnoreCase(direct.getRelationStatus())
                && !isBlank(direct.getChildDrirpUrl())) {
            return new ResolutionForwardHop(targetCode, trim(direct.getChildDrirpUrl()));
        }
        if (properties.isResolveAllowParentFallback() && !isBlank(properties.getParentDrirpUrl())) {
            return new ResolutionForwardHop(resolveParentNodeCode(), trim(properties.getParentDrirpUrl()));
        }
        return null;
    }

    public DrirpMessage sendForward(String nextHopUrl, DrirpMessage sourceRequest, ResolveForwardRequestPayload payload) {
        long startedAt = System.currentTimeMillis();
        DrirpClientImpl client = null;
        String effectiveNextHopUrl = trim(nextHopUrl);
        String traceId = sourceRequest == null ? null : sourceRequest.getTraceId();
        boolean success = false;
        int status = 502;
        String errorMessage = "";
        try {
            client = acquireForwardClient(effectiveNextHopUrl);
            DrirpMessage forward = new DrirpMessage(NodeDrirpOpcode.RESOLVE_FORWARD, gson.toJsonTree(payload));
            if (sourceRequest != null) {
                forward.setRid(sourceRequest.getRid());
                forward.setTraceId(sourceRequest.getTraceId());
                forward.setVersion(sourceRequest.getVersion());
            }
            DrirpMessage response = client.send(forward);
            traceId = isBlank(traceId) ? (response == null ? forward.getTraceId() : response.getTraceId()) : traceId;
            success = response != null && response.isSuccess();
            status = success ? 200 : 502;
            if (!success) {
                errorMessage = response == null
                        ? "upstream response is null"
                        : blankIfNull(response.getErrorMessage(), blankIfNull(response.getErrorCode(), "upstream error"));
            }
            return response;
        } catch (DrirpClientException e) {
            invalidateForwardClient(effectiveNextHopUrl, client);
            errorMessage = blankIfNull(e.getMessage(), "drirp forward failed");
            DrirpMessage error = DrirpMessage.error(
                    NodeDrirpOpcode.RESOLVE_FORWARD,
                    sourceRequest == null ? null : sourceRequest.getRid(),
                    "drirp forward failed: " + e.getMessage()
            );
            error.setErrorCode("UPSTREAM_ERROR");
            error.setOk(Boolean.FALSE);
            return error;
        } finally {
            recordOutboundAudit("RESOLVE_FORWARD_OUT", traceId, effectiveNextHopUrl, status, System.currentTimeMillis() - startedAt, success, errorMessage);
        }
    }

    public <T> DrisResponse<T> parseResolveResponse(DrirpMessage response, ResolveType resolveType, Class<T> dataType) {
        if (response == null) {
            return DrisResponse.failure("UPSTREAM_ERROR", "upstream response is null", null);
        }
        if (!response.isSuccess()) {
            DrisResponse<T> fail = DrisResponse.failure(
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
        ResolveForwardResponsePayload payload = toResponsePayload(response.getBody());
        T data = extractResolvedData(payload, response.getBody(), resolveType, dataType);
        if (!isDataValid(data, resolveType)) {
            return DrisResponse.failure("PROTOCOL_ERROR", "upstream resolved data is invalid", null);
        }

        DrisResponse<T> success = DrisResponse.success(data);
        success.setTraceId(response.getTraceId());
        if (response.getTs() != null && response.getTs().longValue() > 0L) {
            success.setTimestamp(Instant.ofEpochMilli(response.getTs().longValue()).toString());
        }
        return success;
    }

    public ResolveForwardRequestPayload toRequestPayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new ResolveForwardRequestPayload();
        }
        try {
            ResolveForwardRequestPayload payload = gson.fromJson(body, ResolveForwardRequestPayload.class);
            return payload == null ? new ResolveForwardRequestPayload() : payload;
        } catch (Exception ignored) {
            return new ResolveForwardRequestPayload();
        }
    }

    public ResolveForwardResponsePayload toResponsePayload(JsonElement body) {
        if (body == null || body.isJsonNull()) {
            return new ResolveForwardResponsePayload();
        }
        try {
            ResolveForwardResponsePayload payload = gson.fromJson(body, ResolveForwardResponsePayload.class);
            return payload == null ? new ResolveForwardResponsePayload() : payload;
        } catch (Exception ignored) {
            return new ResolveForwardResponsePayload();
        }
    }

    public JsonElement toJsonTree(Object source) {
        return gson.toJsonTree(source);
    }

    public DrirpClientBuilder createClientBuilder(String url) {
        DrirpClientBuilder builder = new DrirpClientBuilder()
                .url(url)
                .timeoutMs(normalizeTimeoutMs());
        return drirpSecuritySupport.applyClientSecurity(builder);
    }

    public DrirpClientImpl acquireForwardClient(String nextHopUrl) throws DrirpClientException {
        return SharedDrirpClientPool.acquire(nextHopUrl, normalizeTimeoutMs(), drirpSecuritySupport);
    }

    public void invalidateForwardClient(String nextHopUrl, DrirpClientImpl expectedClient) {
        SharedDrirpClientPool.invalidate(nextHopUrl, normalizeTimeoutMs(), expectedClient);
    }

    public void recordOutboundAudit(String category, String traceId, String path, int status, long durationMs, boolean success, String errorMessage) {
        drirpAuditService.record(category, traceId, "", path, status, durationMs, success, errorMessage);
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

    public <T> T castData(Object source, Class<T> dataType) {
        if (source == null) {
            return null;
        }
        if (dataType.isInstance(source)) {
            return dataType.cast(source);
        }
        try {
            return gson.fromJson(gson.toJsonTree(source), dataType);
        } catch (Exception ignored) {
            return null;
        }
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

    public JsonObject toJsonObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    public DrirpMessage ok(DrirpMessage request, JsonElement body) {
        DrirpMessage response = DrirpMessage.ok(
                request == null ? NodeDrirpOpcode.RESOLVE_FORWARD : request.getCode(),
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
                request == null ? NodeDrirpOpcode.RESOLVE_FORWARD : request.getCode(),
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

    private <S, T> DrisResponse<T> mapResponse(DrisResponse<S> source, Class<T> dataType) {
        if (source == null) {
            return DrisResponse.failure("UPSTREAM_ERROR", "empty local response", null);
        }
        if (!source.isOk()) {
            DrisResponse<T> fail = DrisResponse.failure(source.getCode(), source.getMessage(), source.getDetails());
            fail.setTraceId(source.getTraceId());
            fail.setTimestamp(source.getTimestamp());
            return fail;
        }
        T data = null;
        Object raw = source.getData();
        if (raw != null) {
            if (dataType.isInstance(raw)) {
                data = dataType.cast(raw);
            } else {
                data = gson.fromJson(gson.toJsonTree(raw), dataType);
            }
        }
        DrisResponse<T> success = DrisResponse.success(data);
        success.setTraceId(source.getTraceId());
        success.setTimestamp(source.getTimestamp());
        return success;
    }

    private <T> T extractResolvedData(
            ResolveForwardResponsePayload payload,
            JsonElement rawBody,
            ResolveType resolveType,
            Class<T> dataType
    ) {
        Object valueFromPayload = resolveType == ResolveType.DIGITAL_OBJECT
                ? payload.getDigitalObject()
                : resolveType == ResolveType.RESOURCE ? payload.getResource() : payload.getMetadata();
        T data = castData(valueFromPayload, dataType);
        if (data != null) {
            return data;
        }

        JsonObject body = toJsonObject(rawBody);
        if (body == null) {
            return null;
        }
        JsonElement dataElement = extractDataElement(body, resolveType);
        if (dataElement == null || dataElement.isJsonNull()) {
            return null;
        }
        try {
            return gson.fromJson(dataElement, dataType);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonElement extractDataElement(JsonObject body, ResolveType resolveType) {
        if (body == null) {
            return null;
        }
        if (resolveType == ResolveType.DIGITAL_OBJECT) {
            if (body.has("digitalObject")) {
                return body.get("digitalObject");
            }
            if (body.has("data")) {
                return body.get("data");
            }
            return null;
        }
        if (resolveType == ResolveType.RESOURCE) {
            if (body.has("resource")) {
                return body.get("resource");
            }
            if (body.has("data")) {
                return body.get("data");
            }
            return null;
        }
        return body.has("metadata") ? body.get("metadata") : body.get("data");
    }

    private boolean isDataValid(Object data, ResolveType resolveType) {
        if (data == null) {
            return false;
        }
        if (resolveType == ResolveType.DIGITAL_OBJECT) {
            if (!(data instanceof DigitalObject)) {
                return false;
            }
            return !isBlank(((DigitalObject) data).getIdentifier());
        }
        if (resolveType == ResolveType.RESOURCE) {
            if (!(data instanceof Resource)) {
                return false;
            }
            Resource resource = (Resource) data;
            return !isBlank(resource.getComponentType()) || !isBlank(resource.getLocator());
        }
        if (!(data instanceof IdentifierMetadata)) {
            return false;
        }
        return !isBlank(((IdentifierMetadata) data).getIdentifier());
    }
}




