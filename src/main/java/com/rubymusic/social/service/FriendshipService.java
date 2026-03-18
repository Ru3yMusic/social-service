package com.rubymusic.social.service;

import com.rubymusic.social.model.Friendship;

import java.util.List;
import java.util.UUID;

public interface FriendshipService {

    /** Sends a friend request and publishes a user.friend.request Kafka event */
    Friendship sendRequest(UUID requesterId, UUID addresseeId, String requesterUsername, String requesterPhotoUrl);

    /** Accepts a pending request and publishes a user.friend.accepted Kafka event */
    Friendship acceptRequest(UUID friendshipId, UUID addresseeId, String addresseeUsername, String addresseePhotoUrl);

    void rejectRequest(UUID friendshipId, UUID addresseeId);

    /** Soft-deletes the friendship — supports "Undo remove friend" */
    void removeFriend(UUID friendshipId, UUID requestingUserId);

    /** Returns all accepted, non-deleted friendships for the user */
    List<Friendship> getFriends(UUID userId);

    /** Returns pending incoming requests for the user */
    List<Friendship> getPendingRequests(UUID userId);

    boolean areFriends(UUID userId1, UUID userId2);
}
