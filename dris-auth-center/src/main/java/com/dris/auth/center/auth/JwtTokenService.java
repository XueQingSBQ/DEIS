package com.dris.auth.center.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenService {
    private final KeyPair keyPair;
    private final AuthProperties properties;

    public JwtTokenService(KeyPair keyPair, AuthProperties properties) {
        this.keyPair = keyPair;
        this.properties = properties;
    }

    public String issueToken(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setHeaderParam("kid", properties.getKeyId())
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.getIssuer())
                .setAudience(properties.getAudience())
                .setSubject(user.getUserId())
                .claim("username", user.getUsername())
                .claim("displayName", user.getDisplayName())
                .claim("orgCode", user.getOrgCode())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(Math.max(300L, properties.getAccessTokenTtlSeconds()))))
                .signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256)
                .compact();
    }

    public CurrentUser parse(String token) {
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(keyPair.getPublic())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseClaimsJws(token);
        Claims claims = jws.getBody();
        return new CurrentUser(
                claims.getSubject(),
                asString(claims.get("username")),
                asString(claims.get("displayName")),
                asString(claims.get("orgCode")),
                claims.getId()
        );
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    public String getPublicKeyPem() {
        return toPem(keyPair.getPublic());
    }

    public String getKeyId() {
        return properties.getKeyId();
    }

    public String getIssuer() {
        return properties.getIssuer();
    }

    public String getAudience() {
        return properties.getAudience();
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String toPem(PublicKey publicKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
