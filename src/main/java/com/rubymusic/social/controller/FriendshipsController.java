package com.rubymusic.social.controller;

import com.rubymusic.social.dto.FriendRequestBody;
import com.rubymusic.social.dto.FriendshipResponse;
import com.rubymusic.social.mapper.FriendshipMapper;
import com.rubymusic.social.service.FriendshipService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FriendshipsController implements FriendshipsApi {

    private final FriendshipService friendshipService;
    private final FriendshipMapper friendshipMapper;
    private final HttpServletRequest httpRequest;

    private UUID currentUserId() {
        return UUID.fromString(httpRequest.getHeader("X-User-Id"));
    }

    private String displayName() {
        return httpRequest.getHeader("X-Display-Name");
    }

    private String profilePhotoUrl() {
        return httpRequest.getHeader("X-Profile-Photo-Url");
    }

    @Override
    public ResponseEntity<FriendshipResponse> sendFriendRequest(FriendRequestBody body) {
        var friendship = friendshipService.sendRequest(
                currentUserId(), body.getAddresseeId(), displayName(), profilePhotoUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(friendshipMapper.toDto(friendship));
    }

    @Override
    public ResponseEntity<List<FriendshipResponse>> listFriends() {
        return ResponseEntity.ok(friendshipMapper.toDtoList(friendshipService.getFriends(currentUserId())));
    }

    @Override
    public ResponseEntity<List<FriendshipResponse>> getPendingRequests() {
        return ResponseEntity.ok(friendshipMapper.toDtoList(friendshipService.getPendingRequests(currentUserId())));
    }

    @Override
    public ResponseEntity<FriendshipResponse> acceptFriendRequest(UUID friendshipId) {
        var friendship = friendshipService.acceptRequest(
                friendshipId, currentUserId(), displayName(), profilePhotoUrl());
        return ResponseEntity.ok(friendshipMapper.toDto(friendship));
    }

    @Override
    public ResponseEntity<Void> rejectFriendRequest(UUID friendshipId) {
        friendshipService.rejectRequest(friendshipId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeFriend(UUID friendshipId) {
        friendshipService.removeFriend(friendshipId, currentUserId());
        return ResponseEntity.noContent().build();
    }
}
