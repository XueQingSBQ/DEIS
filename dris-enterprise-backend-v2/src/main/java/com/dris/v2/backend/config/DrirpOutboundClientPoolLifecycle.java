package com.dris.v2.backend.config;

import com.dris.v2.backend.common.drirp.SharedDrirpClientPool;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class DrirpOutboundClientPoolLifecycle {
    private final BackendV2Properties properties;

    public DrirpOutboundClientPoolLifecycle(BackendV2Properties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initialize() {
        BackendV2Properties.DrirpOutboundClientPoolProperties pool = properties == null
                ? null
                : properties.getDrirpOutboundClientPool();
        SharedDrirpClientPool.configure(
                pool == null ? 64 : pool.getMaxEntries(),
                pool == null ? 600000L : pool.getMaxIdleMs(),
                pool == null ? 30000L : pool.getCleanupIntervalMs()
        );
    }

    @PreDestroy
    public void shutdown() {
        SharedDrirpClientPool.closeAll();
    }
}
