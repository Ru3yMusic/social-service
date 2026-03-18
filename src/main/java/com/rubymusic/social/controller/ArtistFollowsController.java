package com.rubymusic.social.controller;

import com.rubymusic.social.dto.FollowStatusResponse;
import com.rubymusic.social.service.ArtistFollowService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ArtistFollowsController implements ArtistFollowsApi {

    private final ArtistFollowService artistFollowService;
    private final HttpServletRequest httpRequest;

    @Override
    public Optional<HttpServletRequest> getRequest() {
        return Optional.of(httpRequest);
    }

    private UUID currentUserId() {
        return UUID.fromString(httpRequest.getHeader("X-User-Id"));
    }

    @Override
    public ResponseEntity<Void> followArtist(UUID artistId) {
        artistFollowService.followArtist(currentUserId(), artistId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unfollowArtist(UUID artistId) {
        artistFollowService.unfollowArtist(currentUserId(), artistId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<FollowStatusResponse> getFollowStatus(UUID artistId) {
        boolean following = artistFollowService.isFollowing(currentUserId(), artistId);
        return ResponseEntity.ok(new FollowStatusResponse().following(following));
    }

    @Override
    public ResponseEntity<List<UUID>> getFollowedArtists() {
        return ResponseEntity.ok(artistFollowService.getFollowedArtists(currentUserId()));
    }
}
