package com.rubymusic.social.client;

import com.rubymusic.social.client.auth.api.InternalApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthUserValidationClientImplTest {

    @Mock private InternalApi internalApi;

    @InjectMocks
    private AuthUserValidationClientImpl client;

    @Test
    void validateUserExists_userFound_doesNotThrow() {
        UUID userId = UUID.randomUUID();

        assertThatCode(() -> client.validateUserExists(userId)).doesNotThrowAnyException();

        verify(internalApi).getInternalUserById(userId);
    }

    @Test
    void validateUserExists_404_throwsNoSuchElement() {
        UUID userId = UUID.randomUUID();
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))
                .when(internalApi).getInternalUserById(userId);

        assertThatThrownBy(() -> client.validateUserExists(userId))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void validateUserExists_5xx_failsClosedAsRuntimeException() {
        UUID userId = UUID.randomUUID();
        doThrow(HttpClientErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Server Error", null, null, null))
                .when(internalApi).getInternalUserById(userId);

        assertThatThrownBy(() -> client.validateUserExists(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fail closed");
    }

    @Test
    void validateUserExists_unexpectedException_failsClosedAsRuntimeException() {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalStateException("connection reset"))
                .when(internalApi).getInternalUserById(userId);

        assertThatThrownBy(() -> client.validateUserExists(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fail closed");
    }
}
