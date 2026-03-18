package com.rubymusic.social.repository;

import com.rubymusic.social.model.Friendship;
import com.rubymusic.social.model.enums.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    Optional<Friendship> findByRequesterIdAndAddresseeId(UUID requesterId, UUID addresseeId);

    List<Friendship> findAllByAddresseeIdAndStatus(UUID addresseeId, FriendshipStatus status);

    List<Friendship> findAllByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status);

    /** Friendships where the user is either side and status = ACCEPTED and not soft-deleted */
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requesterId = :userId OR f.addresseeId = :userId) " +
           "AND f.status = 'ACCEPTED' AND f.deletedAt IS NULL")
    List<Friendship> findAllAcceptedByUserId(UUID userId);

    /** Check if two users are friends (either direction) */
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN TRUE ELSE FALSE END FROM Friendship f WHERE " +
           "((f.requesterId = :userId1 AND f.addresseeId = :userId2) OR " +
           " (f.requesterId = :userId2 AND f.addresseeId = :userId1)) " +
           "AND f.status = 'ACCEPTED' AND f.deletedAt IS NULL")
    boolean areFriends(UUID userId1, UUID userId2);
}
