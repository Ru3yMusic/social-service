package com.rubymusic.social.controller;

import com.rubymusic.social.exception.GlobalExceptionHandler;
import com.rubymusic.social.service.ArtistFollowService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ArtistFollowsControllerTest {

    @Mock private ArtistFollowService artistFollowService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private ArtistFollowsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── followArtist ──────────────────────────────────────────────────────────

    @Test
    void followArtist_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());

        mockMvc.perform(post("/api/v1/social/artists/{artistId}/follow", artistId))
                .andExpect(status().isNoContent());

        verify(artistFollowService).followArtist(userId, artistId);
    }

    // ── unfollowArtist ────────────────────────────────────────────────────────

    @Test
    void unfollowArtist_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());

        mockMvc.perform(delete("/api/v1/social/artists/{artistId}/follow", artistId))
                .andExpect(status().isNoContent());

        verify(artistFollowService).unfollowArtist(userId, artistId);
    }

    // ── getFollowStatus ───────────────────────────────────────────────────────

    @Test
    void getFollowStatus_followingTrue_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(artistFollowService.isFollowing(userId, artistId)).thenReturn(true);

        mockMvc.perform(get("/api/v1/social/artists/{artistId}/follow/status", artistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true));
    }

    @Test
    void getFollowStatus_followingFalse_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(artistFollowService.isFollowing(userId, artistId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/social/artists/{artistId}/follow/status", artistId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false));
    }

    // ── getFollowedArtists ────────────────────────────────────────────────────

    @Test
    void getFollowedArtists_returns200_withList() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(artistFollowService.getFollowedArtists(userId)).thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/v1/social/artists/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value(a1.toString()))
                .andExpect(jsonPath("$[1]").value(a2.toString()));
    }

    @Test
    void getFollowedArtists_empty_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(artistFollowService.getFollowedArtists(userId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/social/artists/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
