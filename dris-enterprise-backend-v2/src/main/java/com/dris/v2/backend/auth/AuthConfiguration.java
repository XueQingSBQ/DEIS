package com.dris.v2.backend.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Bean
    public RestTemplate authRestTemplate() {
        return new RestTemplate();
    }
}
