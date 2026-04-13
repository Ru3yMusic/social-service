package com.rubymusic.social.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

/**
 * Test-only Spring configuration that provides a dynamically generated RSA {@link PublicKey}.
 *
 * <p>Detected by {@code @SpringBootTest} via component scanning (same base package as
 * {@code SocialServiceApplication}). Prevents {@link JwtConfig#jwtPublicKey} from trying
 * to decode the placeholder value in {@code application-test.yml}, because
 * {@link JwtConfig#jwtPublicKey} is annotated with {@code @ConditionalOnMissingBean(PublicKey.class)}.
 *
 * <p>The corresponding private key is exposed via {@link #testKeyPair()} for use in
 * tests that need to sign JWTs.
 */
@Configuration
public class TestPublicKeyConfig {

    @Bean
    public KeyPair testKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    @Bean
    public PublicKey socialTestPublicKey(KeyPair testKeyPair) {
        return testKeyPair.getPublic();
    }
}
