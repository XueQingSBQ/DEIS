package com.dris.v2.backend.service;

import com.dris.v2.backend.common.concurrent.AsyncExecutionSupport;
import com.dris.v2.backend.config.BackendV2Properties;
import com.dris.v2.backend.config.WorkloadExecutorConfiguration;
import com.dris.v2.backend.contract.DrisResponse;
import com.dris.v2.backend.contract.system.VectorSyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class VectorIndexAutoSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(VectorIndexAutoSyncScheduler.class);

    private final BackendV2Properties properties;
    private final SystemOpsService systemOpsService;
    private final FederatedSearchService federatedSearchService;
    private final Executor vectorSyncExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VectorIndexAutoSyncScheduler(
            BackendV2Properties properties,
            SystemOpsService systemOpsService,
            FederatedSearchService federatedSearchService,
            @Qualifier(WorkloadExecutorConfiguration.VECTOR_SYNC_EXECUTOR) Executor vectorSyncExecutor
    ) {
        this.properties = properties;
        this.systemOpsService = systemOpsService;
        this.federatedSearchService = federatedSearchService;
        this.vectorSyncExecutor = vectorSyncExecutor;
    }

    @Scheduled(
            initialDelayString = "${dris.v2.vector-auto-sync-initial-delay-ms:5000}",
            fixedDelayString = "${dris.v2.vector-auto-sync-interval-ms:3000}"
    )
    public void syncVectorIndex() {
        if (!properties.isVectorAutoSyncEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            AsyncExecutionSupport.execute(vectorSyncExecutor, this::runSyncVectorIndex);
        } catch (RuntimeException exception) {
            running.set(false);
            log.warn("vector auto sync submit failed: {}", exception.getMessage());
        }
    }

    private void runSyncVectorIndex() {
        try {
            DrisResponse<VectorSyncResult> response = systemOpsService.syncVectorIndex();
            if (response == null || !response.isOk() || response.getData() == null) {
                log.warn("vector auto sync failed, code={}, message={}",
                        response == null ? "NULL" : response.getCode(),
                        response == null ? "response is null" : response.getMessage());
                return;
            }
            VectorSyncResult result = response.getData();
            if (result.getProcessed() > 0) {
                if (result.getUpdated() > 0 || result.getDeleted() > 0) {
                    federatedSearchService.clearSearchCache();
                }
                log.info("vector auto sync processed={}, updated={}, deleted={}, failed={}",
                        result.getProcessed(), result.getUpdated(), result.getDeleted(), result.getFailed());
            }
        } catch (Exception exception) {
            log.warn("vector auto sync exception: {}", exception.getMessage());
        } finally {
            running.set(false);
        }
    }
}