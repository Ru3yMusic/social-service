package com.rubymusic.social.client;

import com.rubymusic.social.client.auth.model.ServiceTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenProviderTest {

    private static final String BASE_URL = "http://auth-service";
    private static final String SERVICE_NAME = "social-service";
    private static final String SECRET = "supersecret";
    private static final String TOKEN_URL = BASE_URL + "/api/internal/v1/service-token";

    @Mock private RestTemplate authRestTemplate;

    private ServiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ServiceTokenProvider(authRestTemplate, BASE_URL, SERVICE_NAME, SECRET);
    }

    @Test
    void getServiceToken_firstCall_acquiresFromAuthService() {
        ServiceTokenResponse body = new ServiceTokenResponse();
        body.setToken("jwt-token");
        body.setExpiresIn(300);
        when(authRestTemplate.postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        String token = provider.getServiceToken();

        assertThat(token).isEqualTo("jwt-token");
        verify(authRestTemplate).postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class));
    }

    @Test
    void getServiceToken_secondCall_returnsCachedTokenWithoutNetwork() {
        ServiceTokenResponse body = new ServiceTokenResponse();
        body.setToken("jwt-token");
        body.setExpiresIn(300);
        when(authRestTemplate.postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        String first = provider.getServiceToken();
        String second = provider.getServiceToken();

        assertThat(first).isEqualTo("jwt-token");
        assertThat(second).isEqualTo("jwt-token");
        // RestTemplate must be called exactly ONCE — second call hits the cache
        verify(authRestTemplate, times(1))
                .postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class));
    }

    @Test
    void getServiceToken_nullExpiresIn_defaultsTo300s() {
        ServiceTokenResponse body = new ServiceTokenResponse();
        body.setToken("jwt-no-exp");
        body.setExpiresIn(null);
        when(authRestTemplate.postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        assertThat(provider.getServiceToken()).isEqualTo("jwt-no-exp");
    }

    @Test
    void getServiceToken_nullBody_throwsRuntime() {
        when(authRestTemplate.postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertThatThrownBy(() -> provider.getServiceToken())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot acquire service JWT");
    }

    @Test
    void getServiceToken_nullTokenInBody_throwsRuntime() {
        ServiceTokenResponse body = new ServiceTokenResponse();
        body.setToken(null);
        body.setExpiresIn(300);
        when(authRestTemplate.postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        assertThatThrownBy(() -> provider.getServiceToken())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot acquire service JWT");
    }

    @Test
    void getServiceToken_restTemplateFails_throwsRuntime() {
        when(authRestTemplate.postForEntity(eq(TOKEN_URL), any(), eq(ServiceTokenResponse.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> provider.getServiceToken())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot acquire service JWT");
    }
}
