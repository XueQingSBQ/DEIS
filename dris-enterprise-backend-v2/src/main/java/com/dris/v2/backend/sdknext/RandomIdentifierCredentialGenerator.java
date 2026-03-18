package com.dris.v2.backend.sdknext;

import com.dris.sdk.next.infrastructure.IdentifierCredentialGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class RandomIdentifierCredentialGenerator implements IdentifierCredentialGenerator {
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    @Override
    public String nextCredential(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
