package com.dris.v2.backend.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebMvcConfigurer implements WebMvcConfigurer {
    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    public AuthWebMvcConfigurer(JwtAuthenticationInterceptor jwtAuthenticationInterceptor) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/api/v2/**", "/auth/userinfo", "/oauth2/userinfo");
    }
}
