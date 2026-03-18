package com.dris.v2.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class WorkloadExecutorConfiguration {
    public static final String RESOLVE_EXECUTOR = "resolveExecutor";
    public static final String SEARCH_EXECUTOR = "searchExecutor";
    public static final String VECTOR_SYNC_EXECUTOR = "vectorSyncExecutor";

    @Bean(name = RESOLVE_EXECUTOR)
    public Executor resolveExecutor(BackendV2Properties properties) {
        return buildExecutor(
                "resolve-",
                properties == null ? null : properties.getResolveExecutor(),
                2,
                8,
                200,
                60,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(name = SEARCH_EXECUTOR)
    public Executor searchExecutor(BackendV2Properties properties) {
        return buildExecutor(
                "search-",
                properties == null ? null : properties.getSearchExecutor(),
                2,
                8,
                200,
                60,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(name = VECTOR_SYNC_EXECUTOR)
    public Executor vectorSyncExecutor(BackendV2Properties properties) {
        return buildExecutor(
                "vector-sync-",
                properties == null ? null : properties.getVectorSyncExecutor(),
                1,
                1,
                1,
                60,
                resolveRejectionHandler(properties == null ? null : properties.getVectorSyncExecutor(), new ThreadPoolExecutor.AbortPolicy())
        );
    }

    private Executor buildExecutor(
            String threadNamePrefix,
            BackendV2Properties.WorkloadExecutorProperties configured,
            int defaultCorePoolSize,
            int defaultMaxPoolSize,
            int defaultQueueCapacity,
            int defaultKeepAliveSeconds,
            RejectedExecutionHandler defaultHandler
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = configured == null || configured.getCorePoolSize() <= 0
                ? defaultCorePoolSize
                : configured.getCorePoolSize();
        int maxPoolSize = configured == null || configured.getMaxPoolSize() <= 0
                ? defaultMaxPoolSize
                : Math.max(corePoolSize, configured.getMaxPoolSize());
        int queueCapacity = configured == null || configured.getQueueCapacity() <= 0
                ? defaultQueueCapacity
                : configured.getQueueCapacity();
        int keepAliveSeconds = configured == null || configured.getKeepAliveSeconds() <= 0
                ? defaultKeepAliveSeconds
                : configured.getKeepAliveSeconds();

        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(resolveRejectionHandler(configured, defaultHandler));
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler resolveRejectionHandler(
            BackendV2Properties.WorkloadExecutorProperties configured,
            RejectedExecutionHandler fallback
    ) {
        String policy = configured == null || configured.getRejectionPolicy() == null
                ? ""
                : configured.getRejectionPolicy().trim();
        if ("abort".equalsIgnoreCase(policy)) {
            return new ThreadPoolExecutor.AbortPolicy();
        }
        if ("discard".equalsIgnoreCase(policy)) {
            return new ThreadPoolExecutor.DiscardPolicy();
        }
        if ("discard_oldest".equalsIgnoreCase(policy) || "discard-oldest".equalsIgnoreCase(policy)) {
            return new ThreadPoolExecutor.DiscardOldestPolicy();
        }
        if ("caller_runs".equalsIgnoreCase(policy) || "caller-runs".equalsIgnoreCase(policy)) {
            return new ThreadPoolExecutor.CallerRunsPolicy();
        }
        return fallback;
    }
}