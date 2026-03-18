package com.rubymusic.social.service;

import java.util.List;
import java.util.UUID;

public interface ArtistFollowService {

    /** Follows an artist; publishes event so catalog-service increments followers_count */
    void followArtist(UUID userId, UUID artistId);

    void unfollowArtist(UUID userId, UUID artistId);

    List<UUID> getFollowedArtists(UUID userId);

    boolean isFollowing(UUID userId, UUID artistId);

    long getFollowersCount(UUID artistId);
}
