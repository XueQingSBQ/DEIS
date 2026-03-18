package com.dris.auth.center.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {
    @Test
    void issueAndParse_shouldRoundTripCurrentUser() {
        AuthProperties properties = new AuthProperties();
        AuthConfiguration configuration = new AuthConfiguration();
        KeyPair keyPair = configuration.authKeyPair(properties);
        JwtTokenService service = new JwtTokenService(keyPair, properties);

        AuthUser user = new AuthUser();
        user.setUserId("u_123");
        user.setUsername("alice");
        user.setDisplayName("Alice");
        user.setOrgCode("ABCD");

        String token = service.issueToken(user);
        CurrentUser currentUser = service.parse(token);

        assertEquals("u_123", currentUser.getUserId());
        assertEquals("alice", currentUser.getUsername());
        assertEquals("ABCD", currentUser.getOrgCode());
        assertTrue(service.getPublicKeyPem().contains("BEGIN PUBLIC KEY"));
    }

    @Test
    void parse_invalidToken_shouldThrow() {
        AuthProperties properties = new AuthProperties();
        AuthConfiguration configuration = new AuthConfiguration();
        KeyPair keyPair = configuration.authKeyPair(properties);
        JwtTokenService service = new JwtTokenService(keyPair, properties);

        assertThrows(JwtException.class, () -> service.parse("invalid-token"));
    }
}
