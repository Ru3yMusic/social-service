package com.rubymusic.social.service.impl;

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
import java.util.List;
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

    @Override
    @Transactional
    public Friendship sendRequest(UUID requesterId, UUID addresseeId,
                                  String requesterUsername, String requesterPhotoUrl) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("Cannot send a friend request to yourself");
        }

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
        String payload = String.format(
                "{\"requesterId\":\"%s\",\"addresseeId\":\"%s\",\"friendshipId\":\"%s\"," +
                "\"requesterUsername\":\"%s\",\"requesterPhotoUrl\":\"%s\"}",
                requesterId, addresseeId, friendship.getId(),
                requesterUsername != null ? requesterUsername : "",
                requesterPhotoUrl != null ? requesterPhotoUrl : "");
        kafkaTemplate.send(TOPIC_FRIEND_REQUEST, payload);
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

        String payload = String.format(
                "{\"requesterId\":\"%s\",\"addresseeId\":\"%s\",\"friendshipId\":\"%s\"," +
                "\"addresseeUsername\":\"%s\",\"addresseePhotoUrl\":\"%s\"}",
                friendship.getRequesterId(), addresseeId, friendshipId,
                addresseeUsername != null ? addresseeUsername : "",
                addresseePhotoUrl != null ? addresseePhotoUrl : "");
        kafkaTemplate.send(TOPIC_FRIEND_ACCEPTED, payload);
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
