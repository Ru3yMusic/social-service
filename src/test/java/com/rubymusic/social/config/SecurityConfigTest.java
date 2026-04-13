package com.rubymusic.social.config;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.KeyPair;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link SecurityConfig} — verifies the authorization rule matrix
 * using a real Spring context with H2 + test RSA key pair.
 *
 * <p>Scenarios:
 * <ol>
 *   <li>No token on {@code /api/v1/**} → 401 Unauthorized</li>
 *   <li>USER token on {@code /api/internal/v1/**} → 403 Forbidden</li>
 *   <li>Valid USER token on {@code /api/v1/**} → security passes (not 401/403)</li>
 *   <li>Public paths → 200 regardless of auth</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecurityConfig — authorization rule matrix")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeyPair testKeyPair;

    /** Satisfies KafkaTemplate dependency in ArtistFollowServiceImpl + FriendshipServiceImpl. */
    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    // ── No token ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no token on /api/v1/** → 401 Unauthorized")
    void noToken_onProtectedEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/friendships"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("no token on /api/internal/v1/** → 401 Unauthorized")
    void noToken_onInternalEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/internal/v1/anything"))
                .andExpect(status().isUnauthorized());
    }

    // ── Public paths ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("no token on /actuator/health → 200 OK (public)")
    void noToken_onActuatorHealth_returns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // ── Wrong role ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("USER token on /api/internal/v1/** → 403 Forbidden")
    void userToken_onInternalEndpoint_returns403() throws Exception {
        String userToken = buildToken("user-uuid-789", "USER");

        mockMvc.perform(get("/api/internal/v1/anything")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ── Valid token passes security ───────────────────────────────────────────

    @Test
    @DisplayName("valid USER token on /api/v1/** → security passes (not 401 or 403)")
    void validUserToken_onProtectedEndpoint_passesSecurity() throws Exception {
        String userToken = buildToken("user-uuid-789", "USER");

        MvcResult result = mockMvc.perform(get("/api/v1/friendships")
                        .header("Authorization", "Bearer " + userToken))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("Security should pass — expect anything except 401 or 403")
                .isNotIn(401, 403);
    }

    @Test
    @DisplayName("valid SERVICE token on /api/internal/v1/** → security passes (not 401 or 403)")
    void validServiceToken_onInternalEndpoint_passesSecurity() throws Exception {
        String serviceToken = buildToken("realtime-service", "SERVICE");

        MvcResult result = mockMvc.perform(get("/api/internal/v1/anything")
                        .header("Authorization", "Bearer " + serviceToken))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("Security should pass — expect anything except 401 or 403")
                .isNotIn(401, 403);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String buildToken(String subject, String role) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(testKeyPair.getPrivate())
                .compact();
    }
}
