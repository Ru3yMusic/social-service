package com.rubymusic.social.client;

import com.rubymusic.social.client.auth.model.ServiceTokenRequest;
import com.rubymusic.social.client.auth.model.ServiceTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

/**
 * Acquires and caches a service-scoped JWT from auth-service.
 *
 * <p>Token is cached and refreshed 60 s before expiry. Thread-safe via double-checked locking.
 * Uses a dedicated {@code @LoadBalanced} RestTemplate so Eureka resolves "auth-service".
 */
@Slf4j
@Component
public class ServiceTokenProvider {

    private final RestTemplate authRestTemplate;
    private final String baseUrl;
    private final String serviceName;
    private final String serviceSecret;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.MIN;

    public ServiceTokenProvider(
            @Qualifier("authRestTemplate") RestTemplate authRestTemplate,
            @Value("${services.auth.base-url:http://auth-service}") String baseUrl,
            @Value("${services.auth.service-name:social-service}") String serviceName,
            @Value("${services.auth.service-secret:change-me}") String serviceSecret) {
        this.authRestTemplate = authRestTemplate;
        this.baseUrl = baseUrl;
        this.serviceName = serviceName;
        this.serviceSecret = serviceSecret;
    }

    /**
     * Returns a valid service JWT, acquiring a new one from auth-service if necessary.
     *
     * @return signed RS256 JWT with role=SERVICE
     * @throws RuntimeException if token acquisition fails (fail-closed)
     */
    public String getServiceToken() {
        if (cachedToken == null || Instant.now().isAfter(tokenExpiresAt)) {
            refreshToken();
        }
        return cachedToken;
    }

    private synchronized void refreshToken() {
        // Double-checked locking — another thread may have refreshed while we were waiting
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return;
        }
        try {
            ServiceTokenRequest request = new ServiceTokenRequest();
            request.setServiceName(serviceName);
            request.setServiceSecret(serviceSecret);

            ResponseEntity<ServiceTokenResponse> response = authRestTemplate.postForEntity(
                    baseUrl + "/api/internal/v1/service-token",
                    request,
                    ServiceTokenResponse.class);

            ServiceTokenResponse body = response.getBody();
            if (body == null || body.getToken() == null) {
                throw new IllegalStateException("Null token received from auth-service");
            }

            int expiresIn = body.getExpiresIn() != null ? body.getExpiresIn() : 300;
            cachedToken = body.getToken();
            // Refresh 60 s before actual expiry (minimum 30 s window)
            tokenExpiresAt = Instant.now().plusSeconds(Math.max(expiresIn - 60, 30));
            log.info("Service token refreshed for '{}', valid for {}s", serviceName, expiresIn);

        } catch (Exception e) {
            log.error("Failed to acquire service JWT for '{}'", serviceName, e);
            throw new RuntimeException("Cannot acquire service JWT from auth-service", e);
        }
    }
}
