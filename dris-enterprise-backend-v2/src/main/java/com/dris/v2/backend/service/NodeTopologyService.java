package com.dris.v2.backend.service;

import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.DrirpSecuritySupport;
import com.dris.v2.backend.common.drirp.SharedDrirpClientPool;
import com.dris.v2.backend.dao.DirectDescendantDao;
import com.dris.v2.backend.dao.RouteForwardDao;
import com.dris.v2.backend.dto.DirectDescendantView;
import com.dris.v2.backend.dto.AdjacentNodeStatusView;
import com.dris.v2.backend.dto.NodeBootstrapConfig;
import com.dris.v2.backend.dto.NodeRegisterRequest;
import com.dris.v2.backend.dto.NodeRegisterResponse;
import com.dris.v2.backend.dto.NodeShutdownNotifyResponse;
import com.dris.v2.backend.dto.RouteForwardView;
import com.dris.v2.backend.model.BusinessException;
import com.dris.v2.backend.model.DirectDescendantRecord;
import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.model.NodeLevel;
import com.dris.v2.backend.model.RouteForwardRecord;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bdware.drirp.client.DrirpClientImpl;
import org.bdware.drirp.exception.DrirpClientException;
import org.bdware.drirp.model.DrirpMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class NodeTopologyService {
    private static final Logger log = LoggerFactory.getLogger(NodeTopologyService.class);

    private final BackendV2Properties properties;
    private final DirectDescendantDao directDescendantDao;
    private final RouteForwardDao routeForwardDao;
    private final DrirpSecuritySupport drirpSecuritySupport;
    private final DrirpAuditService drirpAuditService;
    private final Map<String, Integer> childProbeFailureMap = new ConcurrentHashMap<String, Integer>();
    private final AtomicBoolean parentHelloConfirmed = new AtomicBoolean(false);
    private final AtomicReference<Instant> parentLastCheckedAt = new AtomicReference<Instant>();
    private final AtomicReference<Instant> parentLastSuccessAt = new AtomicReference<Instant>();
    private final AtomicReference<String> parentLastMessage = new AtomicReference<String>("UNPROBED");

    public NodeTopologyService(BackendV2Properties properties,
                               DirectDescendantDao directDescendantDao,
                               RouteForwardDao routeForwardDao,
                               DrirpSecuritySupport drirpSecuritySupport,
                               DrirpAuditService drirpAuditService) {
        this.properties = properties;
        this.directDescendantDao = directDescendantDao;
        this.routeForwardDao = routeForwardDao;
        this.drirpSecuritySupport = drirpSecuritySupport;
        this.drirpAuditService = drirpAuditService;
    }

    @Transactional
    public NodeRegisterResponse registerChild(NodeRegisterRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "request is required", HttpStatus.BAD_REQUEST);
        }
        String childNodeName = required("childNodeName", request.getChildNodeName());
        String regionCode = required("regionCode", request.getRegionCode());
        String orgCode = required("orgCode", request.getOrgCode());
        String childDrirpUrl = required("childDrirpUrl", request.getChildDrirpUrl());
        if (!regionCode.matches("^[0-9]{4}$")) {
            throw new BusinessException("VALIDATION_ERROR", "regionCode must be 4 digits", HttpStatus.BAD_REQUEST);
        }
        if (!orgCode.matches("^[A-HJ-NP-Z]{4}$")) {
            throw new BusinessException("VALIDATION_ERROR", "orgCode must be 4 uppercase letters excluding I and O", HttpStatus.BAD_REQUEST);
        }

        NodeLevel parentLevel = NodeLevel.parse(properties.getNodeLevel());
        if (parentLevel == NodeLevel.COUNTY) {
            throw new BusinessException("VALIDATION_ERROR", "county node does not accept child node registration", HttpStatus.BAD_REQUEST);
        }
        NodeLevel childLevel = parentLevel.nextLevel();
        if (childLevel == null) {
            throw new BusinessException("VALIDATION_ERROR", "invalid parent level", HttpStatus.BAD_REQUEST);
        }
        validateRegionRule(parentLevel, childLevel, properties.getRegionCode(), regionCode);

        boolean unique = checkGlobalUniqueness(regionCode, orgCode);
        if (!unique) {
            throw new BusinessException("CONFLICT", "node code already exists in global topology", HttpStatus.CONFLICT);
        }

        directDescendantDao.upsertPending(childNodeName, regionCode, orgCode, childLevel.name(), childDrirpUrl);

        NodeBootstrapConfig bootstrapConfig = new NodeBootstrapConfig();
        bootstrapConfig.setNodeName(childNodeName);
        bootstrapConfig.setNodeLevel(childLevel.name());
        bootstrapConfig.setRegionCode(regionCode);
        bootstrapConfig.setOrgCode(orgCode);
        bootstrapConfig.setParentNodeName(properties.getNodeId());
        bootstrapConfig.setParentDrirpUrl(resolveSelfDrirpUrl());
        bootstrapConfig.setDrirpEnabled(true);
        bootstrapConfig.setStartupHelloEnabled(true);

        NodeRegisterResponse response = new NodeRegisterResponse();
        response.setChildNodeName(childNodeName);
        response.setChildLevel(childLevel.name());
        response.setRegionCode(regionCode);
        response.setOrgCode(orgCode);
        response.setRelationStatus("PENDING");
        response.setHealthStatus("UNKNOWN");
        response.setBootstrapConfig(bootstrapConfig);
        return response;
    }

    public DrirpMessage handleUniquenessCheckUp(DrirpMessage request) {
        JsonObject body = toJsonObject(request.getBody());
        String regionCode = body == null ? null : stringValue(body, "regionCode");
        String orgCode = body == null ? null : stringValue(body, "orgCode");
        if (isBlank(regionCode) || isBlank(orgCode)) {
            return error(request, "VALIDATION_ERROR", "regionCode and orgCode are required");
        }
        if (hasParent()) {
            return forwardToParent(NodeDrirpOpcode.NODE_UNIQUENESS_CHECK_UP, body, request);
        }
        boolean unique = isUniqueLocally(regionCode, orgCode);
        JsonObject out = new JsonObject();
        out.addProperty("unique", unique);
        out.addProperty("checkedBy", properties.getNodeId());
        out.addProperty("checkedAt", Instant.now().toString());
        return ok(request, out);
    }

    @Transactional
    public DrirpMessage handleNodeHello(DrirpMessage request) {
        JsonObject body = toJsonObject(request.getBody());
        String childNodeName = body == null ? null : stringValue(body, "nodeName");
        if (isBlank(childNodeName)) {
            childNodeName = body == null ? null : stringValue(body, "nodeId");
        }
        String childCode = body == null ? null : stringValue(body, "nodeCode");
        String childRegionCode = body == null ? null : stringValue(body, "regionCode");
        String childOrgCode = body == null ? null : stringValue(body, "orgCode");
        if (isBlank(childCode) && !isBlank(childRegionCode) && !isBlank(childOrgCode)) {
            childCode = composeCode(childRegionCode, childOrgCode);
        }
        String childDrirpUrl = body == null ? null : stringValue(body, "drirpUrl");
        if (isBlank(childCode) || isBlank(childDrirpUrl)) {
            return error(request, "VALIDATION_ERROR", "nodeCode(or regionCode+orgCode) and drirpUrl are required");
        }
        DirectDescendantDao.ActivationResult activation = directDescendantDao.activateOnHello(childCode, childDrirpUrl);
        if (activation == null) {
            return error(request, "NOT_REGISTERED", "child node is not registered");
        }
        DirectDescendantRecord record = activation.getRecord();
        if (activation.isFirstActivation()) {
            reportRouteInsertToParent(record);
        }
        JsonObject out = new JsonObject();
        out.addProperty("firstActivation", activation.isFirstActivation());
        out.addProperty("childCode", record.getChildCode());
        out.addProperty("childNodeName", record.getChildNodeName());
        out.addProperty("relationStatus", record.getRelationStatus());
        out.addProperty("healthStatus", record.getHealthStatus());
        return ok(request, out);
    }

    @Transactional
    public DrirpMessage handleNodeHeartbeat(DrirpMessage request) {
        JsonObject body = toJsonObject(request.getBody());
        String eventType = body == null ? null : stringValue(body, "eventType");
        if ("HEALTH_PROBE".equalsIgnoreCase(blankIfNull(eventType, ""))) {
            JsonObject probeAck = new JsonObject();
            probeAck.addProperty("acknowledgedBy", properties.getNodeId());
            probeAck.addProperty("probe", true);
            probeAck.addProperty("receivedAt", Instant.now().toString());
            return ok(request, probeAck);
        }
        String childCode = extractNodeCode(body);
        if (isBlank(childCode)) {
            return error(request, "VALIDATION_ERROR", "nodeCode(or regionCode+orgCode) is required");
        }
        Instant now = Instant.now();
        int updated = directDescendantDao.touchActive(childCode, now);
        if (updated <= 0) {
            return error(request, "NOT_REGISTERED", "child node is not registered");
        }
        JsonObject out = new JsonObject();
        out.addProperty("acknowledgedBy", properties.getNodeId());
        out.addProperty("receivedCode", blankIfNull(childCode, ""));
        out.addProperty("receivedAt", now.toString());
        return ok(request, out);
    }

    @Transactional
    public DrirpMessage handleNodeGoodbye(DrirpMessage request) {
        JsonObject body = toJsonObject(request.getBody());
        String childCode = extractNodeCode(body);
        if (isBlank(childCode)) {
            return error(request, "VALIDATION_ERROR", "nodeCode(or regionCode+orgCode) is required");
        }
        Instant now = Instant.now();
        int updated = directDescendantDao.markOffline(childCode, now);
        JsonObject out = new JsonObject();
        out.addProperty("acknowledgedBy", properties.getNodeId());
        out.addProperty("receivedCode", blankIfNull(childCode, ""));
        out.addProperty("matched", updated > 0);
        out.addProperty("receivedAt", now.toString());
        return ok(request, out);
    }

    @Transactional
    public DrirpMessage handleRouteInsertUp(DrirpMessage request) {
        JsonObject body = toJsonObject(request.getBody());
        if (body == null) {
            return error(request, "VALIDATION_ERROR", "route payload is required");
        }
        String targetRegionCode = stringValue(body, "targetRegionCode");
        String targetOrgCode = stringValue(body, "targetOrgCode");
        String targetNodeName = stringValue(body, "targetNodeName");
        if (isBlank(targetNodeName)) {
            targetNodeName = stringValue(body, "targetNodeId");
        }
        String targetLevel = stringValue(body, "targetLevel");
        String senderCode = stringValue(body, "senderCode");
        if (isBlank(senderCode)) {
            String senderRegionCode = stringValue(body, "senderRegionCode");
            String senderOrgCode = stringValue(body, "senderOrgCode");
            if (!isBlank(senderRegionCode) && !isBlank(senderOrgCode)) {
                senderCode = composeCode(senderRegionCode, senderOrgCode);
            }
        }
        String senderNodeName = stringValue(body, "senderNodeName");
        if (isBlank(senderNodeName)) {
            senderNodeName = stringValue(body, "senderNodeId");
        }
        String senderDrirpUrl = stringValue(body, "senderDrirpUrl");
        int distanceFromSender = intValue(body, "distanceFromSender", 1);
        if (isBlank(targetRegionCode) || isBlank(targetOrgCode) || isBlank(targetNodeName) || isBlank(targetLevel) || isBlank(senderCode) || isBlank(senderDrirpUrl)) {
            return error(request, "VALIDATION_ERROR", "route fields are required");
        }
        int hopCount = distanceFromSender + 1;
        routeForwardDao.upsertRoute(
                targetRegionCode,
                targetOrgCode,
                targetNodeName,
                targetLevel,
                senderCode,
                senderDrirpUrl,
                hopCount,
                "DESCENDANT",
                senderNodeName
        );

        if (hasParent()) {
            JsonObject forward = new JsonObject();
            forward.addProperty("targetRegionCode", targetRegionCode);
            forward.addProperty("targetOrgCode", targetOrgCode);
            forward.addProperty("targetNodeName", targetNodeName);
            forward.addProperty("targetLevel", targetLevel);
            forward.addProperty("senderCode", composeCode(properties.getRegionCode(), properties.getOrganizationCode()));
            forward.addProperty("senderNodeName", properties.getNodeId());
            forward.addProperty("senderDrirpUrl", resolveSelfDrirpUrl());
            forward.addProperty("distanceFromSender", hopCount);
            DrirpMessage upResp = sendToParent(NodeDrirpOpcode.ROUTE_INSERT_UP, forward);
            if (upResp == null || !upResp.isSuccess()) {
                String message = upResp == null ? "null response" : upResp.getErrorMessage();
                return error(request, "UPSTREAM_ERROR", "route insert upstream failed: " + message);
            }
        }

        JsonObject out = new JsonObject();
        out.addProperty("acceptedBy", properties.getNodeId());
        out.addProperty("hopCount", hopCount);
        return ok(request, out);
    }

    public void notifyParentHelloOnStartup() {
        if (!properties.isStartupHelloEnabled() || !hasParent()) {
            return;
        }
        int maxAttempts = resolveStartupHelloMaxAttempts();
        long retryIntervalMs = resolveStartupHelloRetryIntervalMs();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (tryNotifyParentHelloOnce("STARTUP#" + attempt)) {
                return;
            }
            if (attempt < maxAttempts) {
                sleepQuietly(retryIntervalMs);
            }
        }
        log.warn("startup NODE_HELLO exhausted, nodeId={}, attempts={}", properties.getNodeId(), maxAttempts);
    }

    private boolean tryNotifyParentHelloOnce(String scene) {
        Instant now = Instant.now();
        parentLastCheckedAt.set(now);
        if (!hasParent()) {
            parentHelloConfirmed.set(true);
            parentLastSuccessAt.set(now);
            parentLastMessage.set("RTAA");
            return true;
        }
        JsonObject body = buildSelfNodeMessage("HELLO");
        try {
            DrirpMessage response = sendToNode(
                    properties.getParentDrirpUrl(),
                    NodeDrirpOpcode.NODE_HELLO,
                    body,
                    "TOPOLOGY_PARENT_HELLO_OUT",
                    resolveTopologyTimeoutMs()
            );
            if (response != null && response.isSuccess()) {
                parentHelloConfirmed.set(true);
                parentLastSuccessAt.set(now);
                parentLastMessage.set("HELLO_OK");
                log.info("NODE_HELLO success, nodeId={}, scene={}", properties.getNodeId(), blankIfNull(scene, ""));
                return true;
            }
            String message = response == null ? "null response" : blankIfNull(response.getErrorMessage(), response.getErrorCode());
            parentHelloConfirmed.set(false);
            parentLastMessage.set(blankIfNull(message, "HELLO_FAILED"));
            log.warn("NODE_HELLO failed, nodeId={}, scene={}, message={}", properties.getNodeId(), blankIfNull(scene, ""), message);
            return false;
        } catch (Exception ex) {
            parentHelloConfirmed.set(false);
            parentLastMessage.set(blankIfNull(ex.getMessage(), "HELLO_EXCEPTION"));
            log.warn("NODE_HELLO exception, nodeId={}, scene={}, error={}", properties.getNodeId(), blankIfNull(scene, ""), ex.getMessage());
            return false;
        }
    }

    public void runTopologyHealthCycle() {
        heartbeatParentIfNeeded();
        probeDirectDescendantsHealth();
    }

    public void heartbeatParentIfNeeded() {
        if (!hasParent()) {
            return;
        }
        if (!parentHelloConfirmed.get() && properties.isStartupHelloEnabled()) {
            boolean helloOk = tryNotifyParentHelloOnce("HEARTBEAT_PRECHECK");
            if (!helloOk) {
                return;
            }
        }
        JsonObject body = buildSelfNodeMessage("HEARTBEAT");
        Instant now = Instant.now();
        parentLastCheckedAt.set(now);
        try {
            DrirpMessage response = sendToNode(
                    properties.getParentDrirpUrl(),
                    NodeDrirpOpcode.NODE_HEARTBEAT,
                    body,
                    "TOPOLOGY_PARENT_HEARTBEAT_OUT",
                    resolveTopologyTimeoutMs()
            );
            if (response != null && response.isSuccess()) {
                parentHelloConfirmed.set(true);
                parentLastSuccessAt.set(now);
                parentLastMessage.set("HEARTBEAT_OK");
                return;
            }
            parentHelloConfirmed.set(false);
            String message = response == null ? "null response" : blankIfNull(response.getErrorMessage(), response.getErrorCode());
            parentLastMessage.set(blankIfNull(message, "HEARTBEAT_FAILED"));
            if (response == null || !response.isSuccess()) {
                log.warn("NODE_HEARTBEAT to parent failed, nodeId={}, message={}",
                        properties.getNodeId(),
                        response == null ? "null response" : response.getErrorMessage());
            }
            if (properties.isStartupHelloEnabled()) {
                tryNotifyParentHelloOnce("HEARTBEAT_FALLBACK");
            }
        } catch (Exception ex) {
            parentHelloConfirmed.set(false);
            parentLastMessage.set(blankIfNull(ex.getMessage(), "HEARTBEAT_EXCEPTION"));
            log.warn("NODE_HEARTBEAT to parent exception, nodeId={}, err={}", properties.getNodeId(), ex.getMessage());
            if (properties.isStartupHelloEnabled()) {
                tryNotifyParentHelloOnce("HEARTBEAT_EXCEPTION");
            }
        }
    }

    public void probeDirectDescendantsHealth() {
        List<DirectDescendantRecord> activeChildren = directDescendantDao.listActiveChildren();
        if (activeChildren == null || activeChildren.isEmpty()) {
            markStaleDirectDescendantsOffline();
            return;
        }
        Instant now = Instant.now();
        boolean probeChildren = isTopologyProbeChildrenEnabled();
        int offlineFailureThreshold = resolveOfflineFailureThreshold();
        for (DirectDescendantRecord child : activeChildren) {
            if (child == null || isBlank(child.getChildCode())) {
                continue;
            }
            if (!probeChildren || isBlank(child.getChildDrirpUrl())) {
                continue;
            }
            try {
                DrirpMessage response = sendToNode(
                        child.getChildDrirpUrl(),
                        NodeDrirpOpcode.NODE_HEARTBEAT,
                        buildSelfNodeMessage("HEALTH_PROBE"),
                        "TOPOLOGY_CHILD_HEARTBEAT_OUT",
                        resolveTopologyTimeoutMs()
                );
                if (response != null && response.isSuccess()) {
                    clearChildProbeFailure(child.getChildCode());
                    directDescendantDao.touchActive(child.getChildCode(), now);
                } else {
                    int failureCount = increaseChildProbeFailure(child.getChildCode());
                    if (failureCount >= offlineFailureThreshold) {
                        directDescendantDao.markOffline(child.getChildCode(), now);
                        clearChildProbeFailure(child.getChildCode());
                    }
                }
            } catch (Exception ex) {
                int failureCount = increaseChildProbeFailure(child.getChildCode());
                if (failureCount >= offlineFailureThreshold) {
                    directDescendantDao.markOffline(child.getChildCode(), now);
                    clearChildProbeFailure(child.getChildCode());
                }
            }
        }
        markStaleDirectDescendantsOffline();
    }

    public int markStaleDirectDescendantsOffline() {
        long thresholdMs = resolveTopologyOfflineThresholdMs();
        if (thresholdMs <= 0L) {
            return 0;
        }
        Instant staleBefore = Instant.now().minusMillis(thresholdMs);
        return directDescendantDao.markStaleOffline(staleBefore);
    }

    public List<AdjacentNodeStatusView> listAdjacentNodeStatuses() {
        List<AdjacentNodeStatusView> out = new ArrayList<AdjacentNodeStatusView>();
        long nowMs = System.currentTimeMillis();
        long offlineThresholdMs = resolveTopologyOfflineThresholdMs();

        if (hasParent()) {
            AdjacentNodeStatusView parent = new AdjacentNodeStatusView();
            parent.setNodeCode(composeCode(properties.getParentRegionCode(), properties.getParentOrganizationCode()));
            parent.setNodeName(properties.getParentNodeId());
            parent.setNodeLevel(parentLevelName());
            parent.setDrirpUrl(properties.getParentDrirpUrl());
            parent.setDirection("PARENT");
            parent.setObserved(true);
            parent.setRelationStatus(parentHelloConfirmed.get() ? "ACTIVE" : "OFFLINE");
            parent.setHealthStatus(parentHelloConfirmed.get() ? "ACTIVE" : "OFFLINE");
            parent.setLastCheckedAt(parentLastCheckedAt.get() == null ? null : parentLastCheckedAt.get().toString());
            parent.setLastSeenAt(parentLastSuccessAt.get() == null ? null : parentLastSuccessAt.get().toString());
            parent.setMessage(parentLastMessage.get());
            long staleMs = computeStaleMs(parentLastSuccessAt.get(), nowMs);
            parent.setStaleMs(staleMs);
            boolean onlineByTime = staleMs >= 0L && (offlineThresholdMs <= 0L || staleMs <= offlineThresholdMs);
            parent.setOnline(parentHelloConfirmed.get() && onlineByTime);
            out.add(parent);
        }

        List<DirectDescendantRecord> records = directDescendantDao.listAll();
        for (DirectDescendantRecord record : records) {
            AdjacentNodeStatusView view = new AdjacentNodeStatusView();
            view.setNodeCode(record.getChildCode());
            view.setNodeName(record.getChildNodeName());
            view.setNodeLevel(record.getChildLevel());
            view.setDrirpUrl(record.getChildDrirpUrl());
            view.setDirection("CHILD");
            view.setObserved(true);
            view.setRelationStatus(record.getRelationStatus());
            view.setHealthStatus(record.getHealthStatus());
            view.setLastCheckedAt(record.getLastSeenAt() == null ? null : record.getLastSeenAt().toString());
            view.setLastSeenAt(record.getLastSeenAt() == null ? null : record.getLastSeenAt().toString());
            view.setMessage("");
            long staleMs = computeStaleMs(record.getLastSeenAt(), nowMs);
            view.setStaleMs(staleMs);
            boolean relationActive = "ACTIVE".equalsIgnoreCase(blankIfNull(record.getRelationStatus(), ""));
            boolean healthActive = "ACTIVE".equalsIgnoreCase(blankIfNull(record.getHealthStatus(), ""));
            boolean onlineByTime = staleMs >= 0L && (offlineThresholdMs <= 0L || staleMs <= offlineThresholdMs);
            view.setOnline(relationActive && healthActive && onlineByTime);
            out.add(view);
        }
        return out;
    }


    private String parentLevelName() {
        NodeLevel selfLevel = NodeLevel.parse(properties.getNodeLevel());
        if (selfLevel == null) {
            return "UNKNOWN";
        }
        if (selfLevel == NodeLevel.COUNTRY) {
            return "RTAA";
        }
        if (selfLevel == NodeLevel.PROVINCE) {
            return "COUNTRY";
        }
        if (selfLevel == NodeLevel.CITY) {
            return "PROVINCE";
        }
        return "CITY";
    }
    public NodeShutdownNotifyResponse notifyConnectedNodesShutdown() {
        NodeShutdownNotifyResponse response = new NodeShutdownNotifyResponse();
        List<String> failedTargets = new ArrayList<String>();
        int total = 0;
        int success = 0;

        JsonObject goodbyeBody = buildSelfNodeMessage("GOODBYE");

        if (hasParent()) {
            total++;
            String parentUrl = properties.getParentDrirpUrl();
            if (sendGoodbye(parentUrl, goodbyeBody, "TOPOLOGY_PARENT_GOODBYE_OUT")) {
                success++;
            } else {
                failedTargets.add("PARENT:" + blankIfNull(parentUrl, ""));
            }
        }

        List<DirectDescendantRecord> activeChildren = directDescendantDao.listActiveChildren();
        if (activeChildren != null) {
            for (DirectDescendantRecord child : activeChildren) {
                if (child == null || isBlank(child.getChildDrirpUrl())) {
                    continue;
                }
                total++;
                if (sendGoodbye(child.getChildDrirpUrl(), goodbyeBody, "TOPOLOGY_CHILD_GOODBYE_OUT")) {
                    success++;
                } else {
                    failedTargets.add(blankIfNull(child.getChildCode(), "") + "@" + blankIfNull(child.getChildDrirpUrl(), ""));
                }
            }
        }

        response.setTotalTargets(total);
        response.setSuccessCount(success);
        response.setFailureCount(total - success);
        response.setFailedTargets(failedTargets);
        response.setNotifiedAt(Instant.now().toString());
        return response;
    }

    public boolean isShutdownNotifyEnabled() {
        BackendV2Properties.TopologyHealthProperties health = properties == null ? null : properties.getTopologyHealth();
        return health == null || health.isNotifyOnShutdown();
    }

    public List<DirectDescendantView> listDirectDescendants() {
        List<DirectDescendantRecord> records = directDescendantDao.listAll();
        List<DirectDescendantView> out = new ArrayList<DirectDescendantView>();
        for (DirectDescendantRecord record : records) {
            DirectDescendantView view = new DirectDescendantView();
            view.setChildCode(record.getChildCode());
            view.setChildNodeName(record.getChildNodeName());
            view.setChildRegionCode(record.getChildRegionCode());
            view.setChildOrgCode(record.getChildOrgCode());
            view.setChildLevel(record.getChildLevel());
            view.setChildDrirpUrl(record.getChildDrirpUrl());
            view.setRelationStatus(record.getRelationStatus());
            view.setHealthStatus(record.getHealthStatus());
            view.setFirstSeenAt(record.getFirstSeenAt() == null ? null : record.getFirstSeenAt().toString());
            view.setLastSeenAt(record.getLastSeenAt() == null ? null : record.getLastSeenAt().toString());
            out.add(view);
        }
        return out;
    }

    public List<RouteForwardView> listRouteForward() {
        List<RouteForwardRecord> records = routeForwardDao.listAll();
        List<RouteForwardView> out = new ArrayList<RouteForwardView>();
        for (RouteForwardRecord record : records) {
            RouteForwardView view = new RouteForwardView();
            view.setTargetCode(record.getTargetCode());
            view.setTargetRegionCode(record.getTargetRegionCode());
            view.setTargetOrgCode(record.getTargetOrgCode());
            view.setTargetNodeName(record.getTargetNodeName());
            view.setTargetLevel(record.getTargetLevel());
            view.setNextHopCode(record.getNextHopCode());
            view.setNextHopDrirpUrl(record.getNextHopDrirpUrl());
            view.setHopCount(record.getHopCount());
            view.setRouteType(record.getRouteType());
            view.setRouteVersion(record.getRouteVersion());
            view.setSourceNodeName(record.getSourceNodeName());
            view.setUpdatedAt(record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString());
            out.add(view);
        }
        return out;
    }

    private void reportRouteInsertToParent(DirectDescendantRecord child) {
        if (!hasParent()) {
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("targetRegionCode", child.getChildRegionCode());
        body.addProperty("targetOrgCode", child.getChildOrgCode());
        body.addProperty("targetNodeName", child.getChildNodeName());
        body.addProperty("targetLevel", child.getChildLevel());
        body.addProperty("senderCode", composeCode(properties.getRegionCode(), properties.getOrganizationCode()));
        body.addProperty("senderNodeName", properties.getNodeId());
        body.addProperty("senderDrirpUrl", resolveSelfDrirpUrl());
        body.addProperty("distanceFromSender", 1);
        DrirpMessage response = sendToParent(NodeDrirpOpcode.ROUTE_INSERT_UP, body);
        if (response == null || !response.isSuccess()) {
            throw new BusinessException("UPSTREAM_ERROR", "route insert upstream failed", HttpStatus.BAD_GATEWAY);
        }
    }

    private boolean checkGlobalUniqueness(String regionCode, String orgCode) {
        if (!hasParent()) {
            return isUniqueLocally(regionCode, orgCode);
        }
        JsonObject body = new JsonObject();
        body.addProperty("regionCode", regionCode);
        body.addProperty("orgCode", orgCode);
        body.addProperty("requestNodeName", properties.getNodeId());
        body.addProperty("requestNodeId", properties.getNodeId());
        DrirpMessage response = sendToParent(NodeDrirpOpcode.NODE_UNIQUENESS_CHECK_UP, body);
        if (response == null || !response.isSuccess()) {
            throw new BusinessException("UPSTREAM_ERROR", "global uniqueness check failed", HttpStatus.BAD_GATEWAY);
        }
        JsonObject out = toJsonObject(response.getBody());
        if (out == null || !out.has("unique")) {
            throw new BusinessException("UPSTREAM_ERROR", "global uniqueness response missing unique", HttpStatus.BAD_GATEWAY);
        }
        return out.get("unique").getAsBoolean();
    }

    private DrirpMessage forwardToParent(int opcode, JsonObject body, DrirpMessage request) {
        DrirpMessage response = sendToParent(opcode, body);
        if (response == null) {
            return error(request, "UPSTREAM_ERROR", "parent response is null");
        }
        if (response.getRid() == null) {
            response.setRid(request.getRid());
        }
        if (response.getCode() == 0) {
            response.setCode(request.getCode());
        }
        return response;
    }

    private DrirpMessage sendToParent(int opcode, JsonObject body) {
        String parentUrl = properties.getParentDrirpUrl();
        if (isBlank(parentUrl)) {
            return null;
        }
        long startedAt = System.currentTimeMillis();
        DrirpClientImpl client = null;
        String effectiveParentUrl = parentUrl == null ? "" : parentUrl.trim();
        long timeoutMs = 5000L;
        String traceId = body == null ? null : stringValue(body, "traceId");
        boolean success = false;
        int status = 502;
        String errorMessage = "";
        try {
            client = SharedDrirpClientPool.acquire(effectiveParentUrl, timeoutMs, drirpSecuritySupport);
            DrirpMessage response = client.send(opcode, body);
            traceId = isBlank(traceId) ? (response == null ? null : response.getTraceId()) : traceId;
            success = response != null && response.isSuccess();
            status = success ? 200 : 502;
            if (!success) {
                errorMessage = response == null
                        ? "upstream response is null"
                        : blankIfNull(response.getErrorMessage(), blankIfNull(response.getErrorCode(), "upstream error"));
            }
            return response;
        } catch (DrirpClientException e) {
            SharedDrirpClientPool.invalidate(effectiveParentUrl, timeoutMs, client);
            errorMessage = blankIfNull(e.getMessage(), "drirp request failed");
            throw new BusinessException("UPSTREAM_ERROR", "drirp request to parent failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        } finally {
            drirpAuditService.record(
                    "TOPOLOGY_PARENT_OUT",
                    traceId,
                    "",
                    effectiveParentUrl,
                    status,
                    System.currentTimeMillis() - startedAt,
                    success,
                    errorMessage
            );
        }
    }

    private DrirpMessage sendToNode(String url, int opcode, JsonObject body, String auditCategory, long timeoutMs) {
        if (isBlank(url)) {
            return null;
        }
        long startedAt = System.currentTimeMillis();
        DrirpClientImpl client = null;
        String effectiveUrl = url.trim();
        long effectiveTimeoutMs = timeoutMs <= 0L ? 3000L : timeoutMs;
        String traceId = body == null ? null : stringValue(body, "traceId");
        boolean success = false;
        int status = 502;
        String errorMessage = "";
        try {
            client = SharedDrirpClientPool.acquire(effectiveUrl, effectiveTimeoutMs, drirpSecuritySupport);
            DrirpMessage response = client.send(opcode, body);
            traceId = isBlank(traceId) ? (response == null ? null : response.getTraceId()) : traceId;
            success = response != null && response.isSuccess();
            status = success ? 200 : 502;
            if (!success) {
                errorMessage = response == null
                        ? "upstream response is null"
                        : blankIfNull(response.getErrorMessage(), blankIfNull(response.getErrorCode(), "upstream error"));
            }
            return response;
        } catch (DrirpClientException e) {
            SharedDrirpClientPool.invalidate(effectiveUrl, effectiveTimeoutMs, client);
            errorMessage = blankIfNull(e.getMessage(), "drirp request failed");
            throw new BusinessException("UPSTREAM_ERROR", "drirp request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        } finally {
            drirpAuditService.record(
                    blankIfNull(auditCategory, "TOPOLOGY_NODE_OUT"),
                    traceId,
                    "",
                    effectiveUrl,
                    status,
                    System.currentTimeMillis() - startedAt,
                    success,
                    errorMessage
            );
        }
    }

    private boolean isUniqueLocally(String regionCode, String orgCode) {
        if (safeEquals(regionCode, properties.getRegionCode()) && safeEquals(orgCode, properties.getOrganizationCode())) {
            return false;
        }
        if (directDescendantDao.existsByCode(regionCode, orgCode)) {
            return false;
        }
        return !routeForwardDao.existsByCode(regionCode, orgCode);
    }

    private void validateRegionRule(NodeLevel parentLevel, NodeLevel childLevel, String parentRegionCode, String childRegionCode) {
        if (childLevel == NodeLevel.COUNTY && !safeEquals(parentRegionCode, childRegionCode)) {
            throw new BusinessException("VALIDATION_ERROR", "county child must use the same regionCode as parent city", HttpStatus.BAD_REQUEST);
        }
        if (parentLevel == NodeLevel.PROVINCE && childLevel == NodeLevel.CITY) {
            String parentPrefix = parentRegionCode == null || parentRegionCode.length() < 2 ? "" : parentRegionCode.substring(0, 2);
            String childPrefix = childRegionCode == null || childRegionCode.length() < 2 ? "" : childRegionCode.substring(0, 2);
            if (!safeEquals(parentPrefix, childPrefix)) {
                throw new BusinessException("VALIDATION_ERROR", "city child regionCode must remain in the same province prefix", HttpStatus.BAD_REQUEST);
            }
        }
        if (parentLevel == NodeLevel.CITY && childLevel == NodeLevel.COUNTY && !safeEquals(parentRegionCode, childRegionCode)) {
            throw new BusinessException("VALIDATION_ERROR", "county child regionCode must equal parent city regionCode", HttpStatus.BAD_REQUEST);
        }
    }

    private String resolveSelfDrirpUrl() {
        if (!isBlank(properties.getDrirpPublicUrl())) {
            return properties.getDrirpPublicUrl().trim();
        }
        return "grpc://127.0.0.1:" + properties.getDrirpPort();
    }

    private boolean hasParent() {
        return !isBlank(properties.getParentDrirpUrl());
    }

    private boolean sendGoodbye(String targetUrl, JsonObject body, String auditCategory) {
        try {
            DrirpMessage response = sendToNode(
                    targetUrl,
                    NodeDrirpOpcode.NODE_GOODBYE,
                    body,
                    auditCategory,
                    resolveTopologyTimeoutMs()
            );
            return response != null && response.isSuccess();
        } catch (Exception ex) {
            return false;
        }
    }

    private JsonObject buildSelfNodeMessage(String eventType) {
        JsonObject body = new JsonObject();
        body.addProperty("nodeName", properties.getNodeId());
        body.addProperty("nodeId", properties.getNodeId());
        body.addProperty("nodeCode", composeCode(properties.getRegionCode(), properties.getOrganizationCode()));
        body.addProperty("regionCode", properties.getRegionCode());
        body.addProperty("orgCode", properties.getOrganizationCode());
        body.addProperty("nodeLevel", NodeLevel.parse(properties.getNodeLevel()).name());
        body.addProperty("drirpUrl", resolveSelfDrirpUrl());
        body.addProperty("eventType", blankIfNull(eventType, ""));
        body.addProperty("sentAt", Instant.now().toString());
        return body;
    }

    private long resolveTopologyTimeoutMs() {
        BackendV2Properties.TopologyHealthProperties health = properties == null ? null : properties.getTopologyHealth();
        long timeoutMs = health == null ? 3000L : health.getTimeoutMs();
        return timeoutMs <= 0L ? 3000L : timeoutMs;
    }

    private long resolveTopologyOfflineThresholdMs() {
        BackendV2Properties.TopologyHealthProperties health = properties == null ? null : properties.getTopologyHealth();
        long thresholdMs = health == null ? 45000L : health.getOfflineThresholdMs();
        return thresholdMs <= 0L ? 45000L : thresholdMs;
    }

    private boolean isTopologyProbeChildrenEnabled() {
        BackendV2Properties.TopologyHealthProperties health = properties == null ? null : properties.getTopologyHealth();
        return health == null || health.isProbeChildrenEnabled();
    }

    private int resolveOfflineFailureThreshold() {
        BackendV2Properties.TopologyHealthProperties health = properties == null ? null : properties.getTopologyHealth();
        int threshold = health == null ? 3 : health.getOfflineFailureThreshold();
        return threshold <= 0 ? 1 : threshold;
    }

    private int resolveStartupHelloMaxAttempts() {
        BackendV2Properties.TopologyHealthProperties health = properties == null ? null : properties.getTopologyHealth();
        int maxAttempts = health == null ? 3 : health.getStartupHelloMaxAttempts();
        return maxAttempts <= 0 ? 1 : maxAttempts;
    }

    private long resolveStartupHelloRetryIntervalMs() {
        BackendV2Properties.TopologyHealthProperties health = properties == null ? null : properties.getTopologyHealth();
        long retryMs = health == null ? 2000L : health.getStartupHelloRetryIntervalMs();
        return retryMs < 0L ? 0L : retryMs;
    }

    private int increaseChildProbeFailure(String childCode) {
        if (isBlank(childCode)) {
            return 0;
        }
        Integer latest = childProbeFailureMap.compute(childCode.trim(), (key, oldValue) ->
                Integer.valueOf((oldValue == null ? 0 : oldValue.intValue()) + 1));
        return latest == null ? 0 : latest.intValue();
    }

    private void clearChildProbeFailure(String childCode) {
        if (isBlank(childCode)) {
            return;
        }
        childProbeFailureMap.remove(childCode.trim());
    }

    private static void sleepQuietly(long milliseconds) {
        if (milliseconds <= 0L) {
            return;
        }
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String composeCode(String regionCode, String orgCode) {
        return (regionCode == null ? "" : regionCode.trim()) + (orgCode == null ? "" : orgCode.trim());
    }

    private static String extractNodeCode(JsonObject body) {
        if (body == null) {
            return null;
        }
        String nodeCode = stringValue(body, "nodeCode");
        if (!isBlank(nodeCode)) {
            return nodeCode.trim();
        }
        String regionCode = stringValue(body, "regionCode");
        String orgCode = stringValue(body, "orgCode");
        if (!isBlank(regionCode) && !isBlank(orgCode)) {
            return composeCode(regionCode, orgCode);
        }
        return null;
    }

    private String required(String field, String value) {
        if (isBlank(value)) {
            throw new BusinessException("VALIDATION_ERROR", field + " is required", HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String blankIfNull(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private static boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static String stringValue(JsonObject body, String key) {
        if (body == null || !body.has(key) || body.get(key).isJsonNull()) {
            return null;
        }
        return body.get(key).getAsString();
    }

    private static int intValue(JsonObject body, String key, int defaultValue) {
        if (body == null || !body.has(key) || body.get(key).isJsonNull()) {
            return defaultValue;
        }
        try {
            return body.get(key).getAsInt();
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static long computeStaleMs(Instant lastSeenAt, long nowMs) {
        if (lastSeenAt == null) {
            return -1L;
        }
        long stale = nowMs - lastSeenAt.toEpochMilli();
        return stale < 0L ? 0L : stale;
    }

    private static JsonObject toJsonObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static DrirpMessage ok(DrirpMessage request, JsonObject body) {
        DrirpMessage response = DrirpMessage.ok(request.getCode(), request.getRid(), body);
        response.setOk(Boolean.TRUE);
        return response;
    }

    private static DrirpMessage error(DrirpMessage request, String errorCode, String message) {
        DrirpMessage response = DrirpMessage.error(request.getCode(), request.getRid(), message);
        response.setErrorCode(errorCode);
        response.setErrorMessage(message);
        response.setOk(Boolean.FALSE);
        return response;
    }
}





