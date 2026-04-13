package com.rubymusic.social;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SocialServiceApplicationTests {

    /** Satisfies KafkaTemplate dependency in ArtistFollowServiceImpl + FriendshipServiceImpl. */
    @MockBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void contextLoads() {
    }
}
