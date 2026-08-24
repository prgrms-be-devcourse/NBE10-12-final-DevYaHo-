package com.wellbuying.auth.oauth;

import com.wellbuying.auth.dto.LoginResponse;
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

    // 1회용 교환 코드에 로그인 응답(토큰)을 저장 - TTL 60초
    public void save(String code, LoginResponse loginResponse) {
        String json = objectMapper.writeValueAsString(loginResponse);
        redisTemplate.opsForValue().set(key(code), json, TTL);
    }

    // 교환 코드를 조회 후 즉시 삭제(1회용) - 없거나 이미 사용됐으면 empty
    public Optional<LoginResponse> consume(String code) {
        String json = redisTemplate.opsForValue().getAndDelete(key(code));
        if (json == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(json, LoginResponse.class));
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}
