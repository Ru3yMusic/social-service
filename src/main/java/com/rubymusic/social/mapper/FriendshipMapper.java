package com.rubymusic.social.mapper;

import com.rubymusic.social.dto.FriendshipResponse;
import com.rubymusic.social.model.Friendship;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FriendshipMapper {

    FriendshipResponse toDto(Friendship friendship);

    List<FriendshipResponse> toDtoList(List<Friendship> friendships);
}
