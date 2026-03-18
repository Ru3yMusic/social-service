package com.rubymusic.social.repository;

import com.rubymusic.social.model.ArtistFollow;
import com.rubymusic.social.model.id.ArtistFollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArtistFollowRepository extends JpaRepository<ArtistFollow, ArtistFollowId> {

    List<ArtistFollow> findAllByUserId(UUID userId);

    boolean existsByIdUserIdAndIdArtistId(UUID userId, UUID artistId);

    long countByIdArtistId(UUID artistId);
}
