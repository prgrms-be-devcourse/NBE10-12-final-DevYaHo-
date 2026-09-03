package com.wellbuying.domain.member.mail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EmailCooldownGuardTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private EmailCooldownGuard emailCooldownGuard;

    // SETNX가 성공(키가 없었음)하면 예외 없이 통과하는지 검증
    @Test
    void 쿨다운_키가_없으면_선점에_성공한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("email:cooldown:verification:test@example.com", "1",
                Duration.ofSeconds(30))).thenReturn(true);

        emailCooldownGuard.acquire("verification", "test@example.com", 30L);

        verify(valueOperations).setIfAbsent(eq("email:cooldown:verification:test@example.com"), eq("1"),
                eq(Duration.ofSeconds(30)));
    }

    // SETNX가 실패(이미 쿨다운 중)하면 EMAIL_VERIFICATION_COOLDOWN 예외가 발생하는지 검증
    @Test
    void 쿨다운_키가_이미_있으면_선점에_실패하고_예외가_발생한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("email:cooldown:verification:test@example.com", "1",
                Duration.ofSeconds(30))).thenReturn(false);

        assertThatThrownBy(() -> emailCooldownGuard.acquire("verification", "test@example.com", 30L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_COOLDOWN);
    }
}
