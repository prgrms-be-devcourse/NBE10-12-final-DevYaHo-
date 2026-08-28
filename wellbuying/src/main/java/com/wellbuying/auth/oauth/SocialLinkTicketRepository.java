package com.wellbuying.auth.oauth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SocialLinkTicketRepository {

    private static final String KEY_PREFIX = "SocialLink:";
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final String STATE_KEY_PREFIX = "SocialLinkState:";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public SocialLinkTicketRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 로그인 상태에서 소셜 계정 연동을 시작할 때 memberId를 노출하지 않기 위한 1회용 토큰 발급 - TTL 60초
    public String issue(Long memberId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key(token), memberId.toString(), TTL);
        return token;
    }

    // 토큰을 조회 후 즉시 삭제(1회용) - 없거나 이미 사용됐으면 empty
    public Optional<Long> consume(String token) {
        String memberId = redisTemplate.opsForValue().getAndDelete(key(token));
        return Optional.ofNullable(memberId).map(Long::valueOf);
    }

    // 인가요청의 state에 연동 대상 memberId를 결합해 저장 - 콜백 시점에 "이 특정 인가요청에서 발급된 연동인지"를 검증하기 위함
    // TTL 5분: 소셜 제공자 로그인 페이지 체류 시간을 감안해 link_token(60초)보다 넉넉하게 설정
    public void bindState(String state, Long memberId) {
        redisTemplate.opsForValue().set(stateKey(state), memberId.toString(), STATE_TTL);
    }

    // state로 조회 후 즉시 삭제(1회용) - 없거나 이미 사용됐으면 empty(=연동 아닌 일반 로그인으로 처리)
    public Optional<Long> consumeByState(String state) {
        if (state == null) {
            return Optional.empty();
        }
        String memberId = redisTemplate.opsForValue().getAndDelete(stateKey(state));
        return Optional.ofNullable(memberId).map(Long::valueOf);
    }

    private String key(String token) {
        return KEY_PREFIX + token;
    }

    private String stateKey(String state) {
        return STATE_KEY_PREFIX + state;
    }
}
