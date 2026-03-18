package com.dris.v2.backend.auth;

import com.dris.v2.backend.model.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {
    private final JwtTokenVerifier jwtTokenVerifier;
    private final AuthProperties authProperties;

    public JwtAuthenticationInterceptor(JwtTokenVerifier jwtTokenVerifier, AuthProperties authProperties) {
        this.jwtTokenVerifier = jwtTokenVerifier;
        this.authProperties = authProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request != null && "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request == null ? null : request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            if (!authProperties.isApiAuthRequired()) {
                CurrentUserContext.clear();
                return true;
            }
            throw new BusinessException("UNAUTHORIZED", "Authorization Bearer token is required", HttpStatus.UNAUTHORIZED);
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException("UNAUTHORIZED", "access token is empty", HttpStatus.UNAUTHORIZED);
        }
        try {
            CurrentUserContext.set(jwtTokenVerifier.parse(token));
            return true;
        } catch (Exception ex) {
            CurrentUserContext.clear();
            if (!authProperties.isApiAuthRequired()) {
                return true;
            }
            throw new BusinessException("UNAUTHORIZED", "access token is invalid", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }
}
