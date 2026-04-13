package com.rubymusic.social.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * Uses Mockito to stub {@link JwtTokenProvider} — no Spring context needed.
 * Tests verify the filter's behavior across all token states.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── No token ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("no Authorization header → SecurityContext stays empty, chain continues")
    void doFilter_noAuthHeader_noAuthenticationSet() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).parseAndValidate(any());
    }

    @Test
    @DisplayName("Authorization header without 'Bearer ' prefix → ignored, chain continues")
    void doFilter_authHeaderWithoutBearerPrefix_noAuthenticationSet() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtTokenProvider, never()).parseAndValidate(any());
    }

    // ── Valid tokens ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid SERVICE token → sets ROLE_SERVICE authentication in SecurityContext")
    void doFilter_validServiceToken_setsServiceRoleAuthentication() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("realtime-service");
        when(claims.get("role", String.class)).thenReturn("SERVICE");
        when(jwtTokenProvider.parseAndValidate("service-jwt")).thenReturn(claims);

        request.addHeader("Authorization", "Bearer service-jwt");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("realtime-service");
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_SERVICE");
    }

    @Test
    @DisplayName("valid USER token → sets ROLE_USER authentication in SecurityContext")
    void doFilter_validUserToken_setsUserRoleAuthentication() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("user-abc-123");
        when(claims.get("role", String.class)).thenReturn("USER");
        when(jwtTokenProvider.parseAndValidate("user-jwt")).thenReturn(claims);

        request.addHeader("Authorization", "Bearer user-jwt");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("user-abc-123");
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("token with null role → defaults to ROLE_USER")
    void doFilter_tokenWithNullRole_defaultsToUserRole() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("legacy-user");
        when(claims.get("role", String.class)).thenReturn(null);
        when(jwtTokenProvider.parseAndValidate("token-no-role")).thenReturn(claims);

        request.addHeader("Authorization", "Bearer token-no-role");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_USER");
    }

    // ── Invalid tokens ────────────────────────────────────────────────────────

    @Test
    @DisplayName("invalid token → SecurityContext cleared, chain continues (let SecurityConfig reject)")
    void doFilter_invalidToken_clearsContextAndContinuesChain() throws Exception {
        when(jwtTokenProvider.parseAndValidate("bad-jwt"))
                .thenThrow(new JwtException("Invalid"));

        request.addHeader("Authorization", "Bearer bad-jwt");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("expired token → SecurityContext cleared, chain continues")
    void doFilter_expiredToken_clearsContextAndContinuesChain() throws Exception {
        when(jwtTokenProvider.parseAndValidate("expired-jwt"))
                .thenThrow(new ExpiredJwtException(null, null, "Token expired"));

        request.addHeader("Authorization", "Bearer expired-jwt");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
