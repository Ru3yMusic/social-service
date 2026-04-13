package com.rubymusic.social.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubymusic.social.client.AuthUserValidationClient;
import com.rubymusic.social.model.Friendship;
import com.rubymusic.social.model.enums.FriendshipStatus;
import com.rubymusic.social.repository.FriendshipRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FriendshipServiceImpl}.
 *
 * <p>TDD cycle for addressee validation against auth-service:
 * <ul>
 *   <li>RED  — test written before validation logic exists in the impl</li>
 *   <li>GREEN — validation call added to {@code sendRequest()}</li>
 *   <li>TRIANGULATE — happy-path case added to force real logic (not trivial pass)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FriendshipServiceImpl — addressee validation")
class FriendshipServiceImplTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private AuthUserValidationClient authUserValidationClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FriendshipServiceImpl friendshipService;

    // ── RED / GREEN ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendRequest() — addressee not found in auth-service → throws NoSuchElementException, friendship never saved")
    void sendRequest_addresseeNotFound_throwsAndDoesNotSave() {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();

        doThrow(new NoSuchElementException("User not found: " + addresseeId))
                .when(authUserValidationClient).validateUserExists(addresseeId);

        assertThrows(NoSuchElementException.class,
                () -> friendshipService.sendRequest(requesterId, addresseeId, "alice", null));

        verify(authUserValidationClient).validateUserExists(addresseeId);
        verify(friendshipRepository, never()).save(any());
    }

    // ── TRIANGULATE ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendRequest() — addressee exists in auth-service → friendship created with PENDING status")
    void sendRequest_addresseeExists_friendshipSavedWithPendingStatus() {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();

        Friendship saved = Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .build();

        doNothing().when(authUserValidationClient).validateUserExists(addresseeId);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId))
                .thenReturn(Optional.empty());
        when(friendshipRepository.save(any(Friendship.class))).thenReturn(saved);

        Friendship result = friendshipService.sendRequest(
                requesterId, addresseeId, "alice", "photo.jpg");

        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.getRequesterId()).isEqualTo(requesterId);
        assertThat(result.getAddresseeId()).isEqualTo(addresseeId);
        verify(authUserValidationClient).validateUserExists(addresseeId);
        verify(friendshipRepository).save(any(Friendship.class));
    }

    // ── JSON safety (Task 4.1) ────────────────────────────────────────────────

    @Test
    @DisplayName("sendRequest() — username with quotes and backslashes → Kafka message is valid JSON")
    void sendRequest_specialCharactersInUsername_producesValidJson() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        String dangerousUsername = "user\"with\\quotes&<special>";

        Friendship saved = Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .build();

        doNothing().when(authUserValidationClient).validateUserExists(addresseeId);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId))
                .thenReturn(Optional.empty());
        when(friendshipRepository.save(any(Friendship.class))).thenReturn(saved);

        assertDoesNotThrow(() ->
                friendshipService.sendRequest(requesterId, addresseeId, dangerousUsername, null));

        // Capture the JSON sent to Kafka and verify it is valid + contains the escaped username
        verify(kafkaTemplate).send(eq(TOPIC_FRIEND_REQUEST), anyString());

        // Verify the captured JSON can be parsed back without error
        org.mockito.ArgumentCaptor<String> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC_FRIEND_REQUEST), payloadCaptor.capture());

        String json = payloadCaptor.getValue();
        // Must parse without exception — proves it is well-formed JSON
        assertDoesNotThrow(() -> new ObjectMapper().readTree(json));

        // The username must be recoverable after Jackson round-trip
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(json);
        assertThat(node.get("requesterUsername").asText()).isEqualTo(dangerousUsername);
    }

    private static final String TOPIC_FRIEND_REQUEST = "user.friend.request";
}
