package com.rubymusic.social.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubymusic.social.client.AuthUserValidationClient;
import com.rubymusic.social.model.Friendship;
import com.rubymusic.social.model.enums.FriendshipStatus;
import com.rubymusic.social.repository.FriendshipRepository;
import com.rubymusic.social.service.FriendshipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendshipServiceImpl implements FriendshipService {

    private static final String TOPIC_FRIEND_REQUEST  = "user.friend.request";
    private static final String TOPIC_FRIEND_ACCEPTED = "user.friend.accepted";

    private final FriendshipRepository friendshipRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AuthUserValidationClient authUserValidationClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Friendship sendRequest(UUID requesterId, UUID addresseeId,
                                  String requesterUsername, String requesterPhotoUrl) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot send a friend request to yourself");
        }

        // Validate addressee exists in auth-service (fail-closed)
        log.debug("Validating addressee {} exists in auth-service before creating friendship", addresseeId);
        authUserValidationClient.validateUserExists(addresseeId);

        friendshipRepository.findByRequesterIdAndAddresseeId(requesterId, addresseeId)
                .ifPresent(f -> {
                    throw new IllegalStateException("Friend request already exists");
                });

        Friendship friendship = Friendship.builder()
                .requesterId(requesterId)
                .addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .build();
        friendship = friendshipRepository.save(friendship);

        // Publish JSON event for realtime-service
        Map<String, String> requestPayload = new HashMap<>();
        requestPayload.put("requesterId", requesterId.toString());
        requestPayload.put("addresseeId", addresseeId.toString());
        requestPayload.put("friendshipId", friendship.getId().toString());
        requestPayload.put("requesterUsername", requesterUsername != null ? requesterUsername : "");
        requestPayload.put("requesterPhotoUrl", requesterPhotoUrl != null ? requesterPhotoUrl : "");
        kafkaTemplate.send(TOPIC_FRIEND_REQUEST, toJson(requestPayload));
        return friendship;
    }

    @Override
    @Transactional
    public Friendship acceptRequest(UUID friendshipId, UUID addresseeId,
                                    String addresseeUsername, String addresseePhotoUrl) {
        Friendship friendship = findById(friendshipId);
        verifyAddressee(friendship, addresseeId);

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Request is not pending");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship = friendshipRepository.save(friendship);

        Map<String, String> acceptPayload = new HashMap<>();
        acceptPayload.put("requesterId", friendship.getRequesterId().toString());
        acceptPayload.put("addresseeId", addresseeId.toString());
        acceptPayload.put("friendshipId", friendshipId.toString());
        acceptPayload.put("addresseeUsername", addresseeUsername != null ? addresseeUsername : "");
        acceptPayload.put("addresseePhotoUrl", addresseePhotoUrl != null ? addresseePhotoUrl : "");
        kafkaTemplate.send(TOPIC_FRIEND_ACCEPTED, toJson(acceptPayload));
        return friendship;
    }

    @Override
    @Transactional
    public void rejectRequest(UUID friendshipId, UUID addresseeId) {
        Friendship friendship = findById(friendshipId);
        verifyAddressee(friendship, addresseeId);
        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }

    @Override
    @Transactional
    public void removeFriend(UUID friendshipId, UUID requestingUserId) {
        Friendship friendship = findById(friendshipId);

        if (!friendship.getRequesterId().equals(requestingUserId) &&
                !friendship.getAddresseeId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Access denied to friendship: " + friendshipId);
        }

        friendship.setStatus(FriendshipStatus.REMOVED);
        friendship.setDeletedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);
    }

    @Override
    public List<Friendship> getFriends(UUID userId) {
        return friendshipRepository.findAllAcceptedByUserId(userId);
    }

    @Override
    public List<Friendship> getPendingRequests(UUID userId) {
        return friendshipRepository.findAllByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING);
    }

    @Override
    public boolean areFriends(UUID userId1, UUID userId2) {
        return friendshipRepository.areFriends(userId1, userId2);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Kafka payload", e);
        }
    }

    private Friendship findById(UUID friendshipId) {
        return friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException("Friendship not found: " + friendshipId));
    }

    private void verifyAddressee(Friendship friendship, UUID addresseeId) {
        if (!friendship.getAddresseeId().equals(addresseeId)) {
            throw new IllegalArgumentException("Only the addressee can perform this action");
        }
    }
}
