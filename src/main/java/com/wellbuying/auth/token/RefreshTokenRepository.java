package com.wellbuying.auth.token;

import com.wellbuying.auth.jwt.JwtProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisHashCommands.HashFieldSetOption;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "ReT:";
    private static final RedisScript<Long> ROTATE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/rotate_refresh_token.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;

    public RefreshTokenRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jwtProperties = jwtProperties;
    }

    // ReT:{memberId} Hash의 deviceId 필드에 refresh token 정보를 저장하고 필드 단위 TTL을 원자적으로 설정 (HSETEX)
    public void save(Long memberId, String deviceId, RefreshTokenValue value) {
        String json = objectMapper.writeValueAsString(value);
        Expiration expiration = Expiration.milliseconds(jwtProperties.refreshTokenExpirationMs());
        redisTemplate.opsForHash()
                .putAndExpire(key(memberId), Map.of(deviceId, json), HashFieldSetOption.upsert(), expiration);
    }

    // memberId+deviceId로 저장된 refresh token 정보 조회 (없으면 empty)
    public Optional<RefreshTokenValue> find(Long memberId, String deviceId) {
        Object value = redisTemplate.opsForHash().get(key(memberId), deviceId);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue((String) value, RefreshTokenValue.class));
    }

    // rotate_refresh_token.lua 실행 - grace 기간 내 경쟁 요청까지 허용하는 RTR 원자적 회전 (1=성공, 0=세션없음, -1=재사용감지로 전체세션삭제)
    public long rotate(Long memberId, String deviceId, String oldTokenHash, String newTokenHash) {
        return redisTemplate.execute(ROTATE_SCRIPT, List.of(key(memberId)),
                deviceId, oldTokenHash, newTokenHash,
                String.valueOf(jwtProperties.refreshTokenExpirationMs() / 1000),
                String.valueOf(jwtProperties.refreshTokenGraceSeconds()),
                String.valueOf(Instant.now().getEpochSecond()));
    }

    // 특정 기기(deviceId)의 refresh token만 삭제 - 해당 기기 로그아웃
    public void delete(Long memberId, String deviceId) {
        redisTemplate.opsForHash().delete(key(memberId), deviceId);
    }

    // 회원의 모든 기기 refresh token 삭제 - 전체 기기 로그아웃
    public void deleteAll(Long memberId) {
        redisTemplate.delete(key(memberId));
    }

    // memberId로 Redis Hash 키(ReT:{memberId}) 생성
    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
