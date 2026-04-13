package com.rubymusic.social.client;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Port for validating that a user exists in auth-service before social operations.
 *
 * <p>Fail-closed: if auth-service is unavailable or returns an unexpected error,
 * implementations MUST throw {@link RuntimeException} to reject the operation.
 */
public interface AuthUserValidationClient {

    /**
     * Validates that the given user ID exists in auth-service.
     *
     * @param userId the user ID to validate
     * @throws NoSuchElementException if auth-service confirms the user does not exist (404)
     * @throws RuntimeException       if auth-service is unreachable (fail-closed)
     */
    void validateUserExists(UUID userId);
}
