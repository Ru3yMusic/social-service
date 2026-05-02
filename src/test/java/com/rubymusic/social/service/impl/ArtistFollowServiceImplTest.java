package com.rubymusic.social.service.impl;

import com.rubymusic.social.model.ArtistFollow;
import com.rubymusic.social.model.id.ArtistFollowId;
import com.rubymusic.social.repository.ArtistFollowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistFollowServiceImplTest {

    @Mock private ArtistFollowRepository artistFollowRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private ArtistFollowServiceImpl service;

    // ── followArtist ──────────────────────────────────────────────────────────

    @Test
    void followArtist_notFollowing_savesAndPublishesKafkaEvent() {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(artistFollowRepository.existsByIdUserIdAndIdArtistId(userId, artistId))
                .thenReturn(false);

        service.followArtist(userId, artistId);

        verify(artistFollowRepository).save(any(ArtistFollow.class));
        verify(kafkaTemplate).send("artist.followed", artistId.toString());
    }

    @Test
    void followArtist_alreadyFollowing_isIdempotent_noSaveNoKafka() {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(artistFollowRepository.existsByIdUserIdAndIdArtistId(userId, artistId))
                .thenReturn(true);

        service.followArtist(userId, artistId);

        verify(artistFollowRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    // ── unfollowArtist ────────────────────────────────────────────────────────

    @Test
    void unfollowArtist_existing_deletesAndPublishes() {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        ArtistFollowId id = new ArtistFollowId(userId, artistId);
        when(artistFollowRepository.existsById(id)).thenReturn(true);

        service.unfollowArtist(userId, artistId);

        verify(artistFollowRepository).deleteById(id);
        verify(kafkaTemplate).send("artist.unfollowed", artistId.toString());
    }

    @Test
    void unfollowArtist_notExisting_noOp() {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        ArtistFollowId id = new ArtistFollowId(userId, artistId);
        when(artistFollowRepository.existsById(id)).thenReturn(false);

        service.unfollowArtist(userId, artistId);

        verify(artistFollowRepository, never()).deleteById(any());
        verifyNoInteractions(kafkaTemplate);
    }

    // ── getFollowedArtists ────────────────────────────────────────────────────

    @Test
    void getFollowedArtists_returnsArtistIds() {
        UUID userId = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        ArtistFollow f1 = ArtistFollow.builder().id(new ArtistFollowId(userId, a1)).build();
        ArtistFollow f2 = ArtistFollow.builder().id(new ArtistFollowId(userId, a2)).build();
        when(artistFollowRepository.findAllByUserId(userId)).thenReturn(List.of(f1, f2));

        List<UUID> result = service.getFollowedArtists(userId);

        assertThat(result).containsExactly(a1, a2);
    }

    @Test
    void getFollowedArtists_empty_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(artistFollowRepository.findAllByUserId(userId)).thenReturn(List.of());

        List<UUID> result = service.getFollowedArtists(userId);

        assertThat(result).isEmpty();
    }

    // ── isFollowing ───────────────────────────────────────────────────────────

    @Test
    void isFollowing_delegatesToRepository_true() {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(artistFollowRepository.existsByIdUserIdAndIdArtistId(userId, artistId))
                .thenReturn(true);

        assertThat(service.isFollowing(userId, artistId)).isTrue();
    }

    @Test
    void isFollowing_delegatesToRepository_false() {
        UUID userId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        when(artistFollowRepository.existsByIdUserIdAndIdArtistId(userId, artistId))
                .thenReturn(false);

        assertThat(service.isFollowing(userId, artistId)).isFalse();
    }

    // ── getFollowersCount ─────────────────────────────────────────────────────

    @Test
    void getFollowersCount_delegatesToRepository() {
        UUID artistId = UUID.randomUUID();
        when(artistFollowRepository.countByIdArtistId(artistId)).thenReturn(42L);

        assertThat(service.getFollowersCount(artistId)).isEqualTo(42L);
    }
}
