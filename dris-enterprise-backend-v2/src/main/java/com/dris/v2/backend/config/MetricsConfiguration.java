package com.dris.v2.backend.config;

import com.dris.v2.backend.metrics.BackendOperationMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfiguration {
    @Bean
    public BackendOperationMetrics backendOperationMetrics() {
        return new BackendOperationMetrics();
    }
}