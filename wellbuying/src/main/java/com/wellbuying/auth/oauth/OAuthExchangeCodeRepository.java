package com.wellbuying.auth.oauth;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class OAuthExchangeCodeRepository {

    private static final String KEY_PREFIX = "OAuthExchange:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OAuthExchangeCodeRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // 1회용 교환 코드에 토큰 발급에 필요한 최소 정보(memberId, role)만 저장 - TTL 60초
    // 토큰 자체는 실제 교환(exchange) 시점에 발급해야 그때 프론트가 보낸 deviceId를 재사용할 수 있음
    public void save(String code, OAuthExchangePayload payload) {
        String json = objectMapper.writeValueAsString(payload);
        redisTemplate.opsForValue().set(key(code), json, TTL);
    }

    // 교환 코드를 조회 후 즉시 삭제(1회용) - 없거나 이미 사용됐으면 empty
    public Optional<OAuthExchangePayload> consume(String code) {
        String json = redisTemplate.opsForValue().getAndDelete(key(code));
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(json, OAuthExchangePayload.class));
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}
