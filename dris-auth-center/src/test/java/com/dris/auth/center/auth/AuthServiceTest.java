package com.dris.auth.center.auth;

import com.dris.auth.center.auth.dto.AuthTokenResponse;
import com.dris.auth.center.auth.dto.LoginRequest;
import com.dris.auth.center.auth.dto.RegisterUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AuthUserDao authUserDao;
    @Mock
    private JwtTokenService jwtTokenService;

    private AuthProperties properties;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(authUserDao, jwtTokenService, properties, passwordEncoder);
    }

    @Test
    void register_shouldInsertUserAndReturnToken() {
        RegisterUserRequest request = new RegisterUserRequest();
        request.setUsername("alice");
        request.setPassword("secret123");
        request.setDisplayName("Alice");
        request.setOrgCode("ABCD");
        when(authUserDao.findByUsername("alice")).thenReturn(null);
        when(jwtTokenService.issueToken(any(AuthUser.class))).thenReturn("jwt-token");

        AuthTokenResponse response = authService.register(request);

        assertEquals("jwt-token", response.getAccessToken());
        assertEquals("alice", response.getUsername());
        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserDao).insert(captor.capture());
        AuthUser inserted = captor.getValue();
        assertEquals("alice", inserted.getUsername());
        assertTrue(passwordEncoder.matches("secret123", inserted.getPasswordHash()));
        assertNotNull(inserted.getUserId());
    }

    @Test
    void login_invalidPassword_shouldThrow() {
        AuthUser user = new AuthUser();
        user.setUserId("u_1");
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setOrgCode("ABCD");
        user.setStatus("active");
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        when(authUserDao.findByUsername("alice")).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        assertThrows(AuthException.class, () -> authService.login(request));
    }
}
