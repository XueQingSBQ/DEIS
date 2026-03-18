package com.dris.auth.center.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public KeyPair authKeyPair(AuthProperties properties) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            SecureRandom random;
            try {
                random = SecureRandom.getInstance("SHA1PRNG");
                random.setSeed(properties.getKeySeed().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                random = new SecureRandom(properties.getKeySeed().getBytes(StandardCharsets.UTF_8));
            }
            generator.initialize(2048, random);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize auth RSA key pair", ex);
        }
    }
}
