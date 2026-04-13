package com.rubymusic.social.client;

import com.rubymusic.social.client.auth.api.InternalApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Calls auth-service's internal API to verify a user exists.
 *
 * <p>Fail-closed: any non-404 error from auth-service is re-thrown as a
 * {@link RuntimeException}, which causes the caller to reject the operation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthUserValidationClientImpl implements AuthUserValidationClient {

    private final InternalApi internalApi;

    @Override
    public void validateUserExists(UUID userId) {
        try {
            internalApi.getInternalUserById(userId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("User {} not found in auth-service", userId);
                throw new NoSuchElementException("User not found: " + userId);
            }
            log.error("Auth-service error validating user {}: {} {}", userId,
                    e.getStatusCode(), e.getMessage());
            throw new RuntimeException(
                    "Auth-service unavailable — fail closed for user " + userId, e);
        } catch (Exception e) {
            log.error("Unexpected error validating user {} in auth-service", userId, e);
            throw new RuntimeException(
                    "Auth-service unavailable — fail closed for user " + userId, e);
        }
    }
}
