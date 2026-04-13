package com.rubymusic.social.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.PublicKey;

/**
 * Parses and validates RS256 JWTs using the RSA public key distributed by auth-service.
 *
 * <p>Callers receive the raw {@link Claims} payload. Any validation failure
 * (expired, wrong signature, malformed) surfaces as a {@link io.jsonwebtoken.JwtException}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final PublicKey jwtPublicKey;

    /**
     * Parses and validates an RS256 JWT token.
     *
     * @param token raw Bearer token string (no "Bearer " prefix)
     * @return verified {@link Claims} payload
     * @throws io.jsonwebtoken.JwtException on any validation failure
     */
    public Claims parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new MalformedJwtException("JWT token cannot be null or empty");
        }
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
