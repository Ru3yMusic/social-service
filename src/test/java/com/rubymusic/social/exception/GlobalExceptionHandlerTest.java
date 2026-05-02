package com.rubymusic.social.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/social/test");
    }

    @Test
    void handleNotFound_returns404_withMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(
                new NoSuchElementException("User not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("status", 404)
                .containsEntry("message", "User not found")
                .containsEntry("path", "/api/v1/social/test");
    }

    @Test
    void handleBadRequest_returns400_withMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(
                new IllegalArgumentException("Invalid status"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Invalid status");
    }

    @Test
    void handleState_returns400_withMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleState(
                new IllegalStateException("Friendship already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Friendship already exists");
    }

    @Test
    void handleConflict_returns409_withGenericMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleConflict(
                new DataIntegrityViolationException("duplicate key"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Request already exists");
    }

    @Test
    void handleGeneric_returns500_withGenericMessage() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(
                new RuntimeException("totally unexpected"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "Internal server error");
    }
}
