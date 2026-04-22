package com.rubymusic.social.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Extracts and validates RS256 JWTs from the {@code Authorization: Bearer} header.
 *
 * <p>Maps the {@code role} claim to a Spring Security authority:
 * {@code SERVICE} → {@code ROLE_SERVICE}, {@code USER} → {@code ROLE_USER}.
 *
 * <p>On a missing or invalid token the filter does NOT reject the request — it simply
 * leaves the {@link org.springframework.security.core.context.SecurityContext} empty.
 * The authorization rules in {@link SecurityConfig} decide what happens next
 * (401 for missing auth, 403 for insufficient role).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token != null) {
            try {
                Claims claims = jwtTokenProvider.parseAndValidate(token);

                String role = claims.get("role", String.class);
                String authority = "ROLE_" + (role != null ? role : "USER");

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(),
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.trace("JWT authenticated: sub={} role={}", claims.getSubject(), role);

            } catch (JwtException e) {
                SecurityContextHolder.clearContext();
                log.debug("JWT validation failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
