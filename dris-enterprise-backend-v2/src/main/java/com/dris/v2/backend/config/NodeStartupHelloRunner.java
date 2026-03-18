package com.dris.v2.backend.config;

import com.dris.v2.backend.service.NodeTopologyService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class NodeStartupHelloRunner implements ApplicationRunner {
    private final BackendV2Properties properties;
    private final NodeTopologyService nodeTopologyService;

    public NodeStartupHelloRunner(BackendV2Properties properties, NodeTopologyService nodeTopologyService) {
        this.properties = properties;
        this.nodeTopologyService = nodeTopologyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isDrirpEnabled()) {
            return;
        }
        nodeTopologyService.notifyParentHelloOnStartup();
    }
}
