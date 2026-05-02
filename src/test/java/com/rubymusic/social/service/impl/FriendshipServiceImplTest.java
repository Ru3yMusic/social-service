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
    private static final String TOPIC_FRIEND_ACCEPTED = "user.friend.accepted";

    // ── sendRequest — guard rails ──────────────────────────────────────────────

    @Test
    @DisplayName("sendRequest() — requester equals addressee → IllegalArgumentException, no validation, no save")
    void sendRequest_selfRequest_throwsIllegalArgument() {
        UUID userId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> friendshipService.sendRequest(userId, userId, "alice", null));

        verifyNoInteractions(authUserValidationClient);
        verify(friendshipRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    // ── sendRequest — duplicate-state branches ─────────────────────────────────

    @Test
    @DisplayName("sendRequest() — existing PENDING → IllegalStateException")
    void sendRequest_existingPending_throwsIllegalState() {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship existing = Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .build();
        doNothing().when(authUserValidationClient).validateUserExists(addresseeId);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> friendshipService.sendRequest(requesterId, addresseeId, "alice", null));

        verify(friendshipRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("sendRequest() — existing ACCEPTED → IllegalStateException")
    void sendRequest_existingAccepted_throwsIllegalState() {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship existing = Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        doNothing().when(authUserValidationClient).validateUserExists(addresseeId);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> friendshipService.sendRequest(requesterId, addresseeId, "alice", null));
    }

    @Test
    @DisplayName("sendRequest() — existing REJECTED → revives as PENDING and publishes Kafka")
    void sendRequest_existingRejected_revivesAsPending() {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship existing = Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.REJECTED)
                .deletedAt(java.time.LocalDateTime.now())
                .build();
        doNothing().when(authUserValidationClient).validateUserExists(addresseeId);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId))
                .thenReturn(Optional.of(existing));
        when(friendshipRepository.save(existing)).thenReturn(existing);

        Friendship result = friendshipService.sendRequest(
                requesterId, addresseeId, "alice", "photo.jpg");

        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.getDeletedAt()).isNull();
        verify(friendshipRepository).save(existing);
        verify(kafkaTemplate).send(eq(TOPIC_FRIEND_REQUEST), anyString());
    }

    @Test
    @DisplayName("sendRequest() — existing REMOVED → revives as PENDING")
    void sendRequest_existingRemoved_revivesAsPending() {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship existing = Friendship.builder()
                .id(UUID.randomUUID())
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.REMOVED)
                .deletedAt(java.time.LocalDateTime.now())
                .build();
        doNothing().when(authUserValidationClient).validateUserExists(addresseeId);
        when(friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId))
                .thenReturn(Optional.of(existing));
        when(friendshipRepository.save(existing)).thenReturn(existing);

        Friendship result = friendshipService.sendRequest(
                requesterId, addresseeId, null, null);

        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(result.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("sendRequest() — null username and photo → still publishes with empty strings")
    void sendRequest_nullUsernameAndPhoto_publishesEmptyStrings() throws Exception {
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

        friendshipService.sendRequest(requesterId, addresseeId, null, null);

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC_FRIEND_REQUEST), captor.capture());
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(captor.getValue());
        assertThat(node.get("requesterUsername").asText()).isEmpty();
        assertThat(node.get("requesterPhotoUrl").asText()).isEmpty();
    }

    // ── acceptRequest ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("acceptRequest() — pending and addressee matches → status ACCEPTED, Kafka published")
    void acceptRequest_pending_setsAcceptedAndPublishes() {
        UUID friendshipId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(friendship)).thenReturn(friendship);

        Friendship result = friendshipService.acceptRequest(
                friendshipId, addresseeId, "bob", "p.jpg");

        assertThat(result.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        verify(kafkaTemplate).send(eq(TOPIC_FRIEND_ACCEPTED), anyString());
    }

    @Test
    @DisplayName("acceptRequest() — friendship not found → IllegalArgumentException")
    void acceptRequest_notFound_throwsIllegalArgument() {
        UUID friendshipId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> friendshipService.acceptRequest(friendshipId, addresseeId, "bob", null));

        verify(friendshipRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("acceptRequest() — caller is not the addressee → IllegalArgumentException")
    void acceptRequest_notAddressee_throwsIllegalArgument() {
        UUID friendshipId = UUID.randomUUID();
        UUID realAddresseeId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(UUID.randomUUID())
                .addresseeId(realAddresseeId)
                .status(FriendshipStatus.PENDING)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThrows(IllegalArgumentException.class,
                () -> friendshipService.acceptRequest(
                        friendshipId, otherUserId, "x", null));

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptRequest() — already ACCEPTED → IllegalStateException")
    void acceptRequest_notPending_throwsIllegalState() {
        UUID friendshipId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(UUID.randomUUID())
                .addresseeId(addresseeId)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThrows(IllegalStateException.class,
                () -> friendshipService.acceptRequest(
                        friendshipId, addresseeId, "bob", null));

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptRequest() — null username/photo → publishes empty strings")
    void acceptRequest_nullUsernameAndPhoto_publishesEmptyStrings() throws Exception {
        UUID friendshipId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(UUID.randomUUID())
                .addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));
        when(friendshipRepository.save(friendship)).thenReturn(friendship);

        friendshipService.acceptRequest(friendshipId, addresseeId, null, null);

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(TOPIC_FRIEND_ACCEPTED), captor.capture());
        com.fasterxml.jackson.databind.JsonNode node = new ObjectMapper().readTree(captor.getValue());
        assertThat(node.get("addresseeUsername").asText()).isEmpty();
        assertThat(node.get("addresseePhotoUrl").asText()).isEmpty();
    }

    // ── rejectRequest ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("rejectRequest() — addressee matches → status REJECTED, no Kafka")
    void rejectRequest_addresseeMatches_setsRejected() {
        UUID friendshipId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(UUID.randomUUID())
                .addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        friendshipService.rejectRequest(friendshipId, addresseeId);

        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.REJECTED);
        verify(friendshipRepository).save(friendship);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("rejectRequest() — caller not addressee → IllegalArgumentException")
    void rejectRequest_notAddressee_throwsIllegalArgument() {
        UUID friendshipId = UUID.randomUUID();
        UUID realAddresseeId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(UUID.randomUUID())
                .addresseeId(realAddresseeId)
                .status(FriendshipStatus.PENDING)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThrows(IllegalArgumentException.class,
                () -> friendshipService.rejectRequest(friendshipId, otherUserId));

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectRequest() — friendship not found → IllegalArgumentException")
    void rejectRequest_notFound_throwsIllegalArgument() {
        UUID friendshipId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> friendshipService.rejectRequest(friendshipId, addresseeId));
    }

    // ── removeFriend ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeFriend() — caller is requester → status REMOVED with deletedAt")
    void removeFriend_byRequester_marksAsRemoved() {
        UUID friendshipId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(requesterId)
                .addresseeId(UUID.randomUUID())
                .status(FriendshipStatus.ACCEPTED)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        friendshipService.removeFriend(friendshipId, requesterId);

        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.REMOVED);
        assertThat(friendship.getDeletedAt()).isNotNull();
        verify(friendshipRepository).save(friendship);
    }

    @Test
    @DisplayName("removeFriend() — caller is addressee → status REMOVED with deletedAt")
    void removeFriend_byAddressee_marksAsRemoved() {
        UUID friendshipId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(UUID.randomUUID())
                .addresseeId(addresseeId)
                .status(FriendshipStatus.ACCEPTED)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        friendshipService.removeFriend(friendshipId, addresseeId);

        assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.REMOVED);
        assertThat(friendship.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("removeFriend() — caller is neither requester nor addressee → IllegalArgumentException")
    void removeFriend_unrelatedUser_throwsIllegalArgument() {
        UUID friendshipId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Friendship friendship = Friendship.builder()
                .id(friendshipId)
                .requesterId(UUID.randomUUID())
                .addresseeId(UUID.randomUUID())
                .status(FriendshipStatus.ACCEPTED)
                .build();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.of(friendship));

        assertThrows(IllegalArgumentException.class,
                () -> friendshipService.removeFriend(friendshipId, otherUserId));

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeFriend() — friendship not found → IllegalArgumentException")
    void removeFriend_notFound_throwsIllegalArgument() {
        UUID friendshipId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(friendshipRepository.findById(friendshipId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> friendshipService.removeFriend(friendshipId, userId));
    }

    // ── getFriends / getPendingRequests / areFriends ───────────────────────────

    @Test
    @DisplayName("getFriends() — delegates to repository")
    void getFriends_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        Friendship f = Friendship.builder().id(UUID.randomUUID()).build();
        when(friendshipRepository.findAllAcceptedByUserId(userId))
                .thenReturn(java.util.List.of(f));

        java.util.List<Friendship> result = friendshipService.getFriends(userId);

        assertThat(result).containsExactly(f);
    }

    @Test
    @DisplayName("getPendingRequests() — delegates to repository with PENDING status")
    void getPendingRequests_delegatesToRepository() {
        UUID userId = UUID.randomUUID();
        Friendship f = Friendship.builder().id(UUID.randomUUID()).build();
        when(friendshipRepository.findAllByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING))
                .thenReturn(java.util.List.of(f));

        assertThat(friendshipService.getPendingRequests(userId)).containsExactly(f);
    }

    @Test
    @DisplayName("areFriends() — delegates to repository")
    void areFriends_delegatesToRepository_true() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(friendshipRepository.areFriends(u1, u2)).thenReturn(true);

        assertThat(friendshipService.areFriends(u1, u2)).isTrue();
    }

    @Test
    @DisplayName("areFriends() — delegates to repository (false)")
    void areFriends_delegatesToRepository_false() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        when(friendshipRepository.areFriends(u1, u2)).thenReturn(false);

        assertThat(friendshipService.areFriends(u1, u2)).isFalse();
    }

    // ── toJson failure path ────────────────────────────────────────────────────

    @Test
    @DisplayName("sendRequest() — Jackson serialization fails → IllegalStateException wraps the cause")
    void sendRequest_jacksonFailure_throwsIllegalState() throws Exception {
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
        // Force the spy to fail when serializing
        doThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {})
                .when(objectMapper).writeValueAsString(any(java.util.Map.class));

        assertThrows(IllegalStateException.class,
                () -> friendshipService.sendRequest(requesterId, addresseeId, "alice", null));
    }
}
