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

    // 쿨다운이 남아있으면 EMAIL_VERIFICATION_COOLDOWN 예외 발생 (발송 전 호출)
    public void check(String purpose, String key) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(purpose, key)))) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_COOLDOWN);
        }
    }

    // 발송 직후 쿨다운 키를 TTL과 함께 선점 (만료되면 자동 삭제되어 재발송 가능)
    public void mark(String purpose, String key, long seconds) {
        redisTemplate.opsForValue().set(buildKey(purpose, key), "1", Duration.ofSeconds(seconds));
    }

    private String buildKey(String purpose, String key) {
        return KEY_PREFIX + purpose + ":" + key;
    }
}
