package com.wellbuying.domain.member.mail;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmailCooldownGuard {

    private static final String KEY_PREFIX = "email:cooldown:";

    private final StringRedisTemplate redisTemplate;

    public EmailCooldownGuard(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // SETNX로 쿨다운 확인+선점을 한 번에 처리 (check+mark 사이의 동시 요청 레이스 제거)
    public void acquire(String purpose, String key, long seconds) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(buildKey(purpose, key), "1", Duration.ofSeconds(seconds));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_COOLDOWN);
        }
    }

    private String buildKey(String purpose, String key) {
        return KEY_PREFIX + purpose + ":" + key;
    }
}
