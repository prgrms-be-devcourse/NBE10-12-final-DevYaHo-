package com.wellbuying.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class SocialLinkTicketRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private SocialLinkTicketRepository socialLinkTicketRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 발급한 토큰으로 memberId를 1회 소비할 수 있고, TTL 60초 이하로 저장되는지 검증
    @Test
    void 토큰을_발급하면_해당_memberId를_소비할_수_있다() {
        String token = socialLinkTicketRepository.issue(42L);

        Long ttlSeconds = redisTemplate.getExpire("SocialLink:" + token, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(60L);

        assertThat(socialLinkTicketRepository.consume(token)).contains(42L);
    }

    // 소비된 토큰은 1회용이므로 재소비 시 empty를 반환하는지 검증
    @Test
    void 소비된_토큰은_재소비할_수_없다() {
        String token = socialLinkTicketRepository.issue(1L);

        socialLinkTicketRepository.consume(token);

        assertThat(socialLinkTicketRepository.consume(token)).isEmpty();
    }

    // 존재하지 않는(발급되지 않은) 토큰을 소비하면 empty를 반환하는지 검증
    @Test
    void 존재하지_않는_토큰을_소비하면_empty를_반환한다() {
        assertThat(socialLinkTicketRepository.consume("no-such-token")).isEmpty();
    }
}
