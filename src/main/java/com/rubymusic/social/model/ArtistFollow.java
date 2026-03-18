package com.rubymusic.social.model;

import com.rubymusic.social.model.id.ArtistFollowId;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "artist_follows")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistFollow {

    @EmbeddedId
    private ArtistFollowId id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private UUID userId;

    /** References catalog-service artist — no cross-service FK */
    @Column(name = "artist_id", insertable = false, updatable = false)
    private UUID artistId;

    @Column(name = "followed_at", nullable = false)
    @Builder.Default
    private LocalDateTime followedAt = LocalDateTime.now();
}
