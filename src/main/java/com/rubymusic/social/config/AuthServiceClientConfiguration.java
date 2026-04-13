package com.rubymusic.social.config;

import com.rubymusic.social.client.ServiceTokenProvider;
import com.rubymusic.social.client.auth.ApiClient;
import com.rubymusic.social.client.auth.api.InternalApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configures the OpenAPI-generated auth-service REST client for M2M communication.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link ServiceTokenProvider} acquires a service JWT via
 *       {@code POST /api/internal/v1/service-token} (no auth required).</li>
 *   <li>The {@link ApiClient} carries a lazy {@code Supplier<String>} that returns the cached
 *       service JWT; it is evaluated on the first actual API call.</li>
 *   <li>{@link InternalApi} uses the {@link ApiClient} for all Internal-tag operations
 *       (user lookup, batch lookup).</li>
 * </ol>
 *
 * <p>Uses a {@code @LoadBalanced} RestTemplate so Eureka resolves "auth-service".
 */
@Configuration
public class AuthServiceClientConfiguration {

    /**
     * Load-balanced RestTemplate shared by both {@link ServiceTokenProvider}
     * (token acquisition) and the generated {@link ApiClient} (user validation).
     */
    @Bean("authRestTemplate")
    @LoadBalanced
    public RestTemplate authRestTemplate() {
        return new RestTemplate();
    }

    /**
     * Generated {@link ApiClient} configured with a lazy bearer-token supplier.
     * The supplier calls {@link ServiceTokenProvider#getServiceToken()} on first use,
     * so no token is requested at startup.
     */
    @Bean
    public ApiClient authApiClient(
            RestTemplate authRestTemplate,
            @Value("${services.auth.base-url:http://auth-service}") String baseUrl,
            ServiceTokenProvider tokenProvider) {
        ApiClient client = new ApiClient(authRestTemplate);
        client.setBasePath(baseUrl);
        client.setBearerToken(tokenProvider::getServiceToken);
        return client;
    }

    /**
     * Generated {@link InternalApi} — exposes {@code issueServiceToken},
     * {@code getInternalUserById}, and {@code getInternalUsersBatch}.
     */
    @Bean
    public InternalApi internalApi(ApiClient authApiClient) {
        return new InternalApi(authApiClient);
    }
}
