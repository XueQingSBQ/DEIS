package com.dris.v2.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenVerifier {
    private final AuthCenterPublicKeyProvider publicKeyProvider;
    private final AuthProperties properties;

    public JwtTokenVerifier(AuthCenterPublicKeyProvider publicKeyProvider, AuthProperties properties) {
        this.publicKeyProvider = publicKeyProvider;
        this.properties = properties;
    }

    public CurrentUser parse(String token) {
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(publicKeyProvider.getPublicKey())
                .requireIssuer(properties.getIssuer())
                .build()
                .parseClaimsJws(token);
        Claims claims = jws.getBody();
        String audience = claims.getAudience();
        if (!isBlank(properties.getAudience()) && !properties.getAudience().equals(audience)) {
            throw new IllegalStateException("jwt audience is invalid");
        }
        return new CurrentUser(
                claims.getSubject(),
                asString(claims.get("username")),
                asString(claims.get("displayName")),
                asString(claims.get("orgCode")),
                claims.getId()
        );
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
