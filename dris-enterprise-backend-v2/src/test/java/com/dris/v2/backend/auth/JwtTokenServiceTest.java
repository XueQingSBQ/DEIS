package com.dris.v2.backend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenServiceTest {
    @Test
    void verifier_shouldParseCentralToken() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        AuthProperties properties = new AuthProperties();
        properties.setIssuer("auth-center");
        properties.setAudience("dris-business-node");
        properties.setPublicKeyPem(pem);

        AuthCenterPublicKeyProvider provider = new AuthCenterPublicKeyProvider(properties, null);
        JwtTokenVerifier verifier = new JwtTokenVerifier(provider, properties);

        Instant now = Instant.now();
        String token = Jwts.builder()
                .setHeaderParam("kid", "kid-1")
                .setId(UUID.randomUUID().toString())
                .setIssuer("auth-center")
                .setAudience("dris-business-node")
                .setSubject("u_123")
                .claim("username", "alice")
                .claim("displayName", "Alice")
                .claim("orgCode", "ABCD")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(300)))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();

        CurrentUser currentUser = verifier.parse(token);

        assertEquals("u_123", currentUser.getUserId());
        assertEquals("alice", currentUser.getUsername());
        assertEquals("ABCD", currentUser.getOrgCode());
    }
}
