package com.dris.v2.backend.auth;

import com.dris.v2.backend.model.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationInterceptorTest {
    @Mock
    private JwtTokenVerifier jwtTokenVerifier;
    @InjectMocks
    private JwtAuthenticationInterceptor interceptor;

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void preHandle_missingBearer_shouldThrowUnauthorized() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        BusinessException ex = assertThrows(BusinessException.class, () -> interceptor.preHandle(request, response, new Object()));

        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void preHandle_validBearer_shouldPopulateContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.addHeader("Authorization", "Bearer jwt-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtTokenVerifier.parse("jwt-token")).thenReturn(new CurrentUser("u_1", "alice", "Alice", "ABCD", "jti-1"));

        interceptor.preHandle(request, response, new Object());

        assertEquals("u_1", CurrentUserContext.require().getUserId());
    }
}
