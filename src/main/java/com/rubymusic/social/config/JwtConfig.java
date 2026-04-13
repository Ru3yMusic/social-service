package com.rubymusic.social.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RSA public key used for JWT validation (RS256).
 *
 * <p>The public key is injected via config-server as a Base64-encoded X.509/SPKI string
 * (PEM headers are stripped automatically). Set {@code JWT_PUBLIC_KEY} environment variable.
 *
 * <p>{@code @ConditionalOnMissingBean} allows tests to override the {@link PublicKey}
 * bean with a dynamically generated test key pair without parsing the placeholder value.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    @ConditionalOnMissingBean(PublicKey.class)
    public PublicKey jwtPublicKey(JwtProperties props) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(stripPem(props.getPublicKey()));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    /** Removes PEM header/footer lines and all whitespace so raw Base64 can be decoded. */
    private String stripPem(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
    }
}
