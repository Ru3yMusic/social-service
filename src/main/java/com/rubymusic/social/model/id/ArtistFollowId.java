package com.rubymusic.social.model.id;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ArtistFollowId implements Serializable {
    private UUID userId;
    private UUID artistId;
}
