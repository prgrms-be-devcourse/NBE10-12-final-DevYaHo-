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

    // state에 바인딩한 memberId를 1회 소비할 수 있고, TTL 5분 이하로 저장되는지 검증
    @Test
    void state에_바인딩하면_해당_memberId를_소비할_수_있다() {
        socialLinkTicketRepository.bindState("state-1", 42L);

        Long ttlSeconds = redisTemplate.getExpire("SocialLinkState:state-1", TimeUnit.SECONDS);
        assertThat(ttlSeconds).isNotNull();
        assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(300L);

        assertThat(socialLinkTicketRepository.consumeByState("state-1")).contains(42L);
    }

    // 소비된 state는 1회용이므로 재소비 시 empty를 반환하는지 검증
    @Test
    void 소비된_state는_재소비할_수_없다() {
        socialLinkTicketRepository.bindState("state-2", 1L);

        socialLinkTicketRepository.consumeByState("state-2");

        assertThat(socialLinkTicketRepository.consumeByState("state-2")).isEmpty();
    }

    // 바인딩되지 않은(무관한) state를 소비하면 empty를 반환하는지 검증 - 세션 잔존 값 오연동 버그(§2-5)의 회귀 테스트
    @Test
    void 바인딩되지_않은_state를_소비하면_empty를_반환한다() {
        assertThat(socialLinkTicketRepository.consumeByState("no-such-state")).isEmpty();
    }

    // state가 null이면(연동 시도가 아닌 일반 로그인) empty를 반환하는지 검증
    @Test
    void state가_null이면_empty를_반환한다() {
        assertThat(socialLinkTicketRepository.consumeByState(null)).isEmpty();
    }
}
