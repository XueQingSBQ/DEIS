package com.dris.v2.backend.service;

import com.dris.v2.backend.config.BackendV2Properties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NodeTopologyHealthScheduler {
    private final BackendV2Properties properties;
    private final NodeTopologyService nodeTopologyService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NodeTopologyHealthScheduler(BackendV2Properties properties, NodeTopologyService nodeTopologyService) {
        this.properties = properties;
        this.nodeTopologyService = nodeTopologyService;
    }

    @Scheduled(
            initialDelayString = "${dris.v2.topology-health.initial-delay-ms:5000}",
            fixedDelayString = "${dris.v2.topology-health.interval-ms:15000}"
    )
    public void scheduleHealthCycle() {
        if (!properties.isDrirpEnabled()) {
            return;
        }
        BackendV2Properties.TopologyHealthProperties health = properties.getTopologyHealth();
        if (health != null && !health.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            nodeTopologyService.runTopologyHealthCycle();
        } finally {
            running.set(false);
        }
    }
}
