package com.dris.v2.backend.auth;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class AuthCenterPublicKeyProvider {
    private final AuthProperties properties;
    private final RestTemplate restTemplate;
    private volatile PublicKey cachedPublicKey;
    private volatile long cachedAt;

    public AuthCenterPublicKeyProvider(AuthProperties properties, RestTemplate authRestTemplate) {
        this.properties = properties;
        this.restTemplate = authRestTemplate;
    }

    public PublicKey getPublicKey() {
        if (!isBlank(properties.getPublicKeyPem())) {
            return parsePublicKey(properties.getPublicKeyPem());
        }
        long now = System.currentTimeMillis();
        PublicKey local = cachedPublicKey;
        if (local != null && now - cachedAt < Math.max(10000L, properties.getPublicKeyRefreshMs())) {
            return local;
        }
        synchronized (this) {
            local = cachedPublicKey;
            now = System.currentTimeMillis();
            if (local != null && now - cachedAt < Math.max(10000L, properties.getPublicKeyRefreshMs())) {
                return local;
            }
            PublicKey fetched = fetchRemotePublicKey();
            cachedPublicKey = fetched;
            cachedAt = now;
            return fetched;
        }
    }

    private PublicKey fetchRemotePublicKey() {
        String baseUrl = trim(properties.getAuthCenterBaseUrl());
        if (isBlank(baseUrl)) {
            throw new IllegalStateException("auth center base url is not configured");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "auth/public-key" : baseUrl + "/auth/public-key";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        ResponseEntity<AuthCenterApiResponse<AuthCenterPublicKeyResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<Object>(headers),
                new ParameterizedTypeReference<AuthCenterApiResponse<AuthCenterPublicKeyResponse>>() {
                }
        );
        AuthCenterApiResponse<AuthCenterPublicKeyResponse> body = response.getBody();
        if (body == null || !body.isOk() || body.getData() == null || isBlank(body.getData().getPublicKeyPem())) {
            throw new IllegalStateException("failed to fetch auth center public key");
        }
        return parsePublicKey(body.getData().getPublicKeyPem());
    }

    static PublicKey parsePublicKey(String pem) {
        try {
            String normalized = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\r", "")
                    .replace("\n", "")
                    .trim();
            byte[] decoded = Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.UTF_8));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("invalid auth center public key", ex);
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
