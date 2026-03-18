package com.dris.v2.backend.config;

import com.dris.v2.backend.model.NodeDrirpOpcode;
import com.dris.v2.backend.service.DrirpOperationService;
import com.dris.v2.backend.service.DrirpResolutionService;
import com.dris.v2.backend.service.DrirpSearchService;
import com.dris.v2.backend.service.NodeTopologyService;
import org.bdware.drirp.server.DrirpDispatcher;
import org.bdware.drirp.server.DrirpGrpcServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class NodeDrirpServerLifecycle {
    private static final Logger log = LoggerFactory.getLogger(NodeDrirpServerLifecycle.class);

    private final BackendV2Properties properties;
    private final NodeTopologyService nodeTopologyService;
    private final DrirpResolutionService drirpResolutionService;
    private final DrirpOperationService drirpOperationService;
    private final DrirpSearchService drirpSearchService;
    private final DrirpSecuritySupport drirpSecuritySupport;
    private DrirpGrpcServer grpcServer;

    public NodeDrirpServerLifecycle(
            BackendV2Properties properties,
            NodeTopologyService nodeTopologyService,
            DrirpResolutionService drirpResolutionService,
            DrirpOperationService drirpOperationService,
            DrirpSearchService drirpSearchService,
            DrirpSecuritySupport drirpSecuritySupport
    ) {
        this.properties = properties;
        this.nodeTopologyService = nodeTopologyService;
        this.drirpResolutionService = drirpResolutionService;
        this.drirpOperationService = drirpOperationService;
        this.drirpSearchService = drirpSearchService;
        this.drirpSecuritySupport = drirpSecuritySupport;
    }

    @PostConstruct
    public void start() {
        if (!properties.isDrirpEnabled()) {
            log.info("DRIRP server is disabled by dris.v2.drirp-enabled=false");
            return;
        }
        try {
            DrirpDispatcher dispatcher = new DrirpDispatcher();
            dispatcher.register(NodeDrirpOpcode.NODE_UNIQUENESS_CHECK_UP, nodeTopologyService::handleUniquenessCheckUp);
            dispatcher.register(NodeDrirpOpcode.NODE_HELLO, nodeTopologyService::handleNodeHello);
            dispatcher.register(NodeDrirpOpcode.NODE_HEARTBEAT, nodeTopologyService::handleNodeHeartbeat);
            dispatcher.register(NodeDrirpOpcode.NODE_GOODBYE, nodeTopologyService::handleNodeGoodbye);
            dispatcher.register(NodeDrirpOpcode.ROUTE_INSERT_UP, nodeTopologyService::handleRouteInsertUp);
            dispatcher.register(NodeDrirpOpcode.RESOLVE_FORWARD, drirpResolutionService::handleResolveForward);
            dispatcher.register(NodeDrirpOpcode.RESOLVE_BATCH_FORWARD, drirpResolutionService::handleResolveBatchForward);
            dispatcher.register(NodeDrirpOpcode.OP_FORWARD, drirpOperationService::handleOperationForward);
            dispatcher.register(NodeDrirpOpcode.SEARCH_FORWARD, drirpSearchService::handleSearchForward);
            drirpSecuritySupport.applyServerSecurity(dispatcher);
            grpcServer = new DrirpGrpcServer(properties.getDrirpPort(), dispatcher);
            grpcServer.start();
            log.info("DRIRP gRPC server started, port={}, nodeId={}", properties.getDrirpPort(), properties.getNodeId());
        } catch (Exception e) {
            throw new IllegalStateException("failed to start DRIRP server: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    public void stop() {
        if (properties.isDrirpEnabled() && nodeTopologyService.isShutdownNotifyEnabled()) {
            try {
                nodeTopologyService.notifyConnectedNodesShutdown();
            } catch (Exception ex) {
                log.warn("shutdown notify connected nodes failed: {}", ex.getMessage());
            }
        }
        if (grpcServer != null) {
            grpcServer.stop();
        }
    }
}


