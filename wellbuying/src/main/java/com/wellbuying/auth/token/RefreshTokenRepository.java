package com.wellbuying.auth.token;

import com.wellbuying.auth.jwt.JwtProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisHashCommands.HashFieldSetOption;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

// Redis 장애 시 login()/reissue()/logout()이 막히지 않도록 서킷 브레이커로 감지해 DB 폴백으로 전환한다
// (phase21 §2-3). Redis 정상화 후에는 reissue()에서 DB 폴백 세션을 발견하는 즉시 Redis로 되돌려
// 자연스럽게 이관한다(§2-4).
@Repository
public class RefreshTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRepository.class);
    private static final String KEY_PREFIX = "ReT:";
    private static final String CIRCUIT_BREAKER_NAME = "redisRefreshToken";
    private static final RedisScript<Long> ROTATE_SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/rotate_refresh_token.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;
    private final RefreshTokenFallbackStore fallbackStore;

    public RefreshTokenRepository(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            JwtProperties jwtProperties, RefreshTokenFallbackStore fallbackStore) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jwtProperties = jwtProperties;
        this.fallbackStore = fallbackStore;
    }

    // ReT:{memberId} Hash의 deviceId 필드에 refresh token 정보를 저장하고 필드 단위 TTL을 원자적으로 설정 (HSETEX)
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "saveFallback")
    public void save(Long memberId, String deviceId, RefreshTokenValue value) {
        putInRedis(memberId, deviceId, value);
    }

    private void saveFallback(Long memberId, String deviceId, RefreshTokenValue value, Throwable t) {
        log.warn("Redis 장애로 DB 폴백에 refresh token 저장 - memberId={}, deviceId={}", memberId, deviceId, t);
        fallbackStore.save(memberId, deviceId, value);
    }

    private void putInRedis(Long memberId, String deviceId, RefreshTokenValue value) {
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

    // memberId의 모든 기기 refresh token 정보 조회 (HGETALL) - deviceId -> RefreshTokenValue
    public Map<String, RefreshTokenValue> findAll(Long memberId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key(memberId));
        Map<String, RefreshTokenValue> result = new LinkedHashMap<>();
        entries.forEach((deviceId, json) ->
                result.put((String) deviceId, objectMapper.readValue((String) json, RefreshTokenValue.class)));
        return result;
    }

    // rotate_refresh_token.lua 실행 - grace 기간 내 경쟁 요청까지 허용하는 RTR 원자적 회전 (1=성공, 0=세션없음, -1=재사용감지로 전체세션삭제)
    // Redis가 정상인데도 세션이 없다면(0) 장애 중 DB 폴백에만 기록된 세션일 수 있으므로 폴백을 조회해
    // 있으면 그대로 회전하고 결과를 Redis로 되돌린다(§2-4 자연 이관)
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "rotateFallback")
    public long rotate(Long memberId, String deviceId, String oldTokenHash, String newTokenHash) {
        long result = redisTemplate.execute(ROTATE_SCRIPT, List.of(key(memberId)),
                deviceId, oldTokenHash, newTokenHash,
                String.valueOf(jwtProperties.refreshTokenExpirationMs() / 1000),
                String.valueOf(jwtProperties.refreshTokenGraceSeconds()),
                String.valueOf(Instant.now().getEpochSecond()));
        if (result != 0) {
            return result;
        }
        return applyFallbackRotate(memberId, deviceId, oldTokenHash, newTokenHash, true);
    }

    private long rotateFallback(Long memberId, String deviceId, String oldTokenHash, String newTokenHash,
            Throwable t) {
        log.warn("Redis 장애로 DB 폴백에서 refresh token 회전 - memberId={}, deviceId={}", memberId, deviceId, t);
        return applyFallbackRotate(memberId, deviceId, oldTokenHash, newTokenHash, false);
    }

    private long applyFallbackRotate(Long memberId, String deviceId, String oldTokenHash, String newTokenHash,
            boolean migrateToRedis) {
        RefreshTokenFallbackStore.RotateResult result = fallbackStore.rotate(memberId, deviceId, oldTokenHash,
                newTokenHash, jwtProperties.refreshTokenGraceSeconds());
        if (migrateToRedis && result.code() == 1) {
            log.info("DB 폴백 세션을 Redis로 이관 - memberId={}, deviceId={}", memberId, deviceId);
            putInRedis(memberId, deviceId, result.value());
            fallbackStore.delete(memberId, deviceId);
        }
        return result.code();
    }

    // 특정 기기(deviceId)의 refresh token만 삭제 - 해당 기기 로그아웃
    // Redis 정상 여부와 무관하게 DB 폴백도 함께 정리한다 - 장애 중 DB에만 기록된 세션이 로그아웃 후에도
    // 살아남아 복구 시 되살아나는 것을 막기 위함(§2-4)
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "deleteFallback")
    public void delete(Long memberId, String deviceId) {
        redisTemplate.opsForHash().delete(key(memberId), deviceId);
        deleteFromFallbackStoreQuietly(memberId, deviceId);
    }

    private void deleteFallback(Long memberId, String deviceId, Throwable t) {
        log.warn("Redis 장애로 DB 폴백에서만 로그아웃 처리 - memberId={}, deviceId={}", memberId, deviceId, t);
        fallbackStore.delete(memberId, deviceId);
    }

    // Redis 삭제는 이미 성공했으므로 로그아웃의 주 목적은 달성한 상태 - DB 폴백 정리 실패가 이 예외를 삼켜
    // 응답 실패로 번지는 것도, 서킷 브레이커가 이를 Redis 장애로 오인해 circuit을 여는 것도 막는다
    private void deleteFromFallbackStoreQuietly(Long memberId, String deviceId) {
        try {
            fallbackStore.delete(memberId, deviceId);
        } catch (Exception e) {
            log.error("Redis 삭제 완료 후 DB 폴백 정리 중 오류 발생 - memberId={}, deviceId={}", memberId, deviceId, e);
        }
    }

    // 회원의 모든 기기 refresh token 삭제 - 전체 기기 로그아웃
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "deleteAllFallback")
    public void deleteAll(Long memberId) {
        redisTemplate.delete(key(memberId));
        deleteAllFromFallbackStoreQuietly(memberId);
    }

    private void deleteAllFallback(Long memberId, Throwable t) {
        log.warn("Redis 장애로 DB 폴백에서만 전체 로그아웃 처리 - memberId={}", memberId, t);
        fallbackStore.deleteAll(memberId);
    }

    private void deleteAllFromFallbackStoreQuietly(Long memberId) {
        try {
            fallbackStore.deleteAll(memberId);
        } catch (Exception e) {
            log.error("Redis 삭제 완료 후 DB 폴백 전체 정리 중 오류 발생 - memberId={}", memberId, e);
        }
    }

    // memberId로 Redis Hash 키(ReT:{memberId}) 생성
    private String key(Long memberId) {
        return KEY_PREFIX + memberId;
    }
}
