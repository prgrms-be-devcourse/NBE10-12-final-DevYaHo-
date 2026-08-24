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

    private String key(String token) {
        return KEY_PREFIX + token;
    }
}
