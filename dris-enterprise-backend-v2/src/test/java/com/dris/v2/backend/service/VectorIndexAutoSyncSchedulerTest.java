package com.dris.v2.backend.service;

import com.dris.v2.backend.config.BackendV2Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class VectorIndexAutoSyncSchedulerTest {
    @Mock
    private SystemOpsService systemOpsService;
    @Mock
    private FederatedSearchService federatedSearchService;
    @Mock
    private Executor vectorSyncExecutor;

    @Test
    void syncVectorIndex_shouldSubmitTaskToExecutor() {
        BackendV2Properties properties = new BackendV2Properties();
        properties.setVectorAutoSyncEnabled(true);
        VectorIndexAutoSyncScheduler scheduler = new VectorIndexAutoSyncScheduler(
                properties,
                systemOpsService,
                federatedSearchService,
                vectorSyncExecutor
        );

        scheduler.syncVectorIndex();

        verify(vectorSyncExecutor).execute(any(Runnable.class));
        verifyNoInteractions(systemOpsService);
    }
}