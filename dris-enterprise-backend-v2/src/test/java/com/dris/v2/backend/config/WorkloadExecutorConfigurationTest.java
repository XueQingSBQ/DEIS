package com.dris.v2.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkloadExecutorConfigurationTest {

    @Test
    void resolveExecutor_shouldUseConfiguredThreadPoolSettings() {
        WorkloadExecutorConfiguration configuration = new WorkloadExecutorConfiguration();
        BackendV2Properties properties = new BackendV2Properties();
        BackendV2Properties.WorkloadExecutorProperties executorProperties = new BackendV2Properties.WorkloadExecutorProperties();
        executorProperties.setCorePoolSize(3);
        executorProperties.setMaxPoolSize(6);
        executorProperties.setQueueCapacity(25);
        executorProperties.setKeepAliveSeconds(45);
        executorProperties.setRejectionPolicy("caller_runs");
        properties.setResolveExecutor(executorProperties);

        Executor executor = configuration.resolveExecutor(properties);

        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertEquals(3, pool.getCorePoolSize());
        assertEquals(6, pool.getMaxPoolSize());
        assertTrue(pool.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);
        pool.shutdown();
    }

    @Test
    void vectorSyncExecutor_shouldDefaultToAbortPolicy() {
        WorkloadExecutorConfiguration configuration = new WorkloadExecutorConfiguration();
        BackendV2Properties properties = new BackendV2Properties();

        Executor executor = configuration.vectorSyncExecutor(properties);

        assertTrue(executor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertEquals(1, pool.getCorePoolSize());
        assertEquals(1, pool.getMaxPoolSize());
        assertTrue(pool.getThreadPoolExecutor().getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy);
        pool.shutdown();
    }
}