package com.dris.v2.backend.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthServiceTest {
    @Test
    void parsePublicKey_shouldReadPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(keyPair.getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";

        PublicKey publicKey = AuthCenterPublicKeyProvider.parsePublicKey(pem);
        PublicKey expected = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyPair.getPublic().getEncoded()));

        assertNotNull(publicKey);
        assertEquals(Base64.getEncoder().encodeToString(expected.getEncoded()), Base64.getEncoder().encodeToString(publicKey.getEncoded()));
    }
}
