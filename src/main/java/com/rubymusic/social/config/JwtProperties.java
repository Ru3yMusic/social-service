package com.rubymusic.social.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code jwt.*} properties from the config-server.
 *
 * <p>Expected property (Base64-encoded X.509/SPKI RSA public key, no PEM headers):
 * <pre>
 *   jwt.public-key: ${JWT_PUBLIC_KEY:}
 * </pre>
 */
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** Base64-encoded RSA public key (X.509/SPKI). May include PEM headers — they are stripped. */
    private String publicKey = "";
}
