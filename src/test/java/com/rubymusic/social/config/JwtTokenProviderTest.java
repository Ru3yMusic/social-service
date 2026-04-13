package com.rubymusic.social.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * TDD RED → GREEN cycle:
 *   RED : JwtTokenProvider exists but returns incorrect results / throws unexpectedly
 *   GREEN: parseAndValidate returns correct Claims; throws JwtException on any invalid token
 */
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static KeyPair keyPair;
    private static KeyPair wrongKeyPair;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        wrongKeyPair = gen.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        // Directly instantiate — no Spring context needed for pure unit test
        jwtTokenProvider = new JwtTokenProvider(keyPair.getPublic());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid SERVICE token → returns claims with correct subject and role")
    void parseAndValidate_validServiceToken_returnsCorrectClaims() {
        String token = buildToken("social-service", "SERVICE", 60_000);

        Claims claims = jwtTokenProvider.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("social-service");
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("valid USER token → returns claims with correct subject and role")
    void parseAndValidate_validUserToken_returnsCorrectClaims() {
        String token = buildToken("user-uuid-456", "USER", 60_000);

        Claims claims = jwtTokenProvider.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("user-uuid-456");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    // ── Failure cases ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("expired token → throws JwtException")
    void parseAndValidate_expiredToken_throwsJwtException() {
        String token = Jwts.builder()
                .subject("stale-user")
                .claim("role", "USER")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(keyPair.getPrivate())
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.parseAndValidate(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("token signed with wrong key → throws JwtException")
    void parseAndValidate_wrongSignature_throwsJwtException() {
        String token = Jwts.builder()
                .subject("attacker")
                .claim("role", "SERVICE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(wrongKeyPair.getPrivate())   // signed with a DIFFERENT key
                .compact();

        assertThatThrownBy(() -> jwtTokenProvider.parseAndValidate(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("malformed token → throws JwtException")
    void parseAndValidate_malformedToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtTokenProvider.parseAndValidate("this.is.not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("null token → throws exception")
    void parseAndValidate_nullToken_throwsException() {
        assertThatThrownBy(() -> jwtTokenProvider.parseAndValidate(null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("empty string token → throws JwtException")
    void parseAndValidate_emptyToken_throwsJwtException() {
        assertThatThrownBy(() -> jwtTokenProvider.parseAndValidate(""))
                .isInstanceOf(JwtException.class);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String buildToken(String subject, String role, long expiryOffsetMs) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryOffsetMs))
                .signWith(keyPair.getPrivate())
                .compact();
    }
}
