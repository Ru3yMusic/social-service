package com.rubymusic.social.controller;

import com.rubymusic.social.dto.FriendshipResponse;
import com.rubymusic.social.exception.GlobalExceptionHandler;
import com.rubymusic.social.mapper.FriendshipMapper;
import com.rubymusic.social.model.Friendship;
import com.rubymusic.social.service.FriendshipService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FriendshipsControllerTest {

    @Mock private FriendshipService friendshipService;
    @Mock private FriendshipMapper friendshipMapper;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private FriendshipsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── sendFriendRequest ─────────────────────────────────────────────────────

    @Test
    void sendFriendRequest_returns201() throws Exception {
        UUID requesterId = UUID.randomUUID();
        UUID addresseeId = UUID.randomUUID();
        Friendship created = mock(Friendship.class);

        when(httpRequest.getHeader("X-User-Id")).thenReturn(requesterId.toString());
        when(httpRequest.getHeader("X-Display-Name")).thenReturn("alice");
        when(httpRequest.getHeader("X-Profile-Photo-Url")).thenReturn("photo.jpg");
        when(friendshipService.sendRequest(requesterId, addresseeId, "alice", "photo.jpg"))
                .thenReturn(created);
        when(friendshipMapper.toDto(created)).thenReturn(new FriendshipResponse());

        mockMvc.perform(post("/api/v1/social/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addresseeId\":\"" + addresseeId + "\"}"))
                .andExpect(status().isCreated());

        verify(friendshipService).sendRequest(requesterId, addresseeId, "alice", "photo.jpg");
    }

    @Test
    void sendFriendRequest_alreadyExists_returns400() throws Exception {
        UUID requesterId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(requesterId.toString());
        when(friendshipService.sendRequest(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Friendship already exists"));

        mockMvc.perform(post("/api/v1/social/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addresseeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── listFriends ───────────────────────────────────────────────────────────

    @Test
    void listFriends_returns200_withMappedList() throws Exception {
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(friendshipService.getFriends(userId))
                .thenReturn(List.of(mock(Friendship.class), mock(Friendship.class)));
        when(friendshipMapper.toDtoList(anyList()))
                .thenReturn(List.of(new FriendshipResponse(), new FriendshipResponse()));

        mockMvc.perform(get("/api/v1/social/friends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ── getPendingRequests ────────────────────────────────────────────────────

    @Test
    void getPendingRequests_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(friendshipService.getPendingRequests(userId))
                .thenReturn(List.of(mock(Friendship.class)));
        when(friendshipMapper.toDtoList(anyList()))
                .thenReturn(List.of(new FriendshipResponse()));

        mockMvc.perform(get("/api/v1/social/friends/requests/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── acceptFriendRequest ───────────────────────────────────────────────────

    @Test
    void acceptFriendRequest_returns200() throws Exception {
        UUID friendshipId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Friendship accepted = mock(Friendship.class);

        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(httpRequest.getHeader("X-Display-Name")).thenReturn("bob");
        when(httpRequest.getHeader("X-Profile-Photo-Url")).thenReturn("p.jpg");
        when(friendshipService.acceptRequest(friendshipId, userId, "bob", "p.jpg"))
                .thenReturn(accepted);
        when(friendshipMapper.toDto(accepted)).thenReturn(new FriendshipResponse());

        mockMvc.perform(post("/api/v1/social/friends/{friendshipId}/accept", friendshipId))
                .andExpect(status().isOk());
    }

    @Test
    void acceptFriendRequest_notFound_returns404() throws Exception {
        UUID friendshipId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(friendshipService.acceptRequest(eq(friendshipId), eq(userId), any(), any()))
                .thenThrow(new NoSuchElementException("Friend request not found"));

        mockMvc.perform(post("/api/v1/social/friends/{friendshipId}/accept", friendshipId))
                .andExpect(status().isNotFound());
    }

    // ── rejectFriendRequest ───────────────────────────────────────────────────

    @Test
    void rejectFriendRequest_returns204() throws Exception {
        UUID friendshipId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());

        mockMvc.perform(post("/api/v1/social/friends/{friendshipId}/reject", friendshipId))
                .andExpect(status().isNoContent());

        verify(friendshipService).rejectRequest(friendshipId, userId);
    }

    // ── removeFriend ──────────────────────────────────────────────────────────

    @Test
    void removeFriend_returns204() throws Exception {
        UUID friendshipId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());

        mockMvc.perform(delete("/api/v1/social/friends/{friendshipId}", friendshipId))
                .andExpect(status().isNoContent());

        verify(friendshipService).removeFriend(friendshipId, userId);
    }
}
