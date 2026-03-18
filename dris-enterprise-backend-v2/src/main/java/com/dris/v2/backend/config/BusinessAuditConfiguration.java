package com.dris.v2.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class BusinessAuditConfiguration {
    public static final String IDENTIFIER_BUSINESS_AUDIT_EXECUTOR = "identifierBusinessAuditExecutor";

    @Bean(name = IDENTIFIER_BUSINESS_AUDIT_EXECUTOR)
    public Executor identifierBusinessAuditExecutor(BackendV2Properties properties) {
        BackendV2Properties.BusinessAuditProperties audit = properties == null
                ? null
                : properties.getBusinessAudit();

        int corePoolSize = audit == null ? 1 : Math.max(1, audit.getCorePoolSize());
        int maxPoolSize = audit == null ? 2 : Math.max(corePoolSize, audit.getMaxPoolSize());
        int queueCapacity = audit == null ? 2000 : Math.max(100, audit.getQueueCapacity());
        int keepAliveSeconds = audit == null ? 60 : Math.max(10, audit.getKeepAliveSeconds());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("identifier-audit-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
