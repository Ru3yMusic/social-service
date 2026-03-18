package com.rubymusic.social.service.impl;

import com.rubymusic.social.model.ArtistFollow;
import com.rubymusic.social.model.id.ArtistFollowId;
import com.rubymusic.social.repository.ArtistFollowRepository;
import com.rubymusic.social.service.ArtistFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistFollowServiceImpl implements ArtistFollowService {

    private static final String TOPIC_ARTIST_FOLLOWED   = "artist.followed";
    private static final String TOPIC_ARTIST_UNFOLLOWED = "artist.unfollowed";

    private final ArtistFollowRepository artistFollowRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    @Transactional
    public void followArtist(UUID userId, UUID artistId) {
        if (artistFollowRepository.existsByIdUserIdAndIdArtistId(userId, artistId)) {
            return; // idempotent
        }
        ArtistFollow follow = ArtistFollow.builder()
                .id(new ArtistFollowId(userId, artistId))
                .build();
        artistFollowRepository.save(follow);

        // Notify catalog-service to increment followers_count
        kafkaTemplate.send(TOPIC_ARTIST_FOLLOWED, artistId.toString());
    }

    @Override
    @Transactional
    public void unfollowArtist(UUID userId, UUID artistId) {
        ArtistFollowId id = new ArtistFollowId(userId, artistId);
        if (artistFollowRepository.existsById(id)) {
            artistFollowRepository.deleteById(id);
            kafkaTemplate.send(TOPIC_ARTIST_UNFOLLOWED, artistId.toString());
        }
    }

    @Override
    public List<UUID> getFollowedArtists(UUID userId) {
        return artistFollowRepository.findAllByUserId(userId)
                .stream()
                .map(f -> f.getId().getArtistId())
                .toList();
    }

    @Override
    public boolean isFollowing(UUID userId, UUID artistId) {
        return artistFollowRepository.existsByIdUserIdAndIdArtistId(userId, artistId);
    }

    @Override
    public long getFollowersCount(UUID artistId) {
        return artistFollowRepository.countByIdArtistId(artistId);
    }
}
