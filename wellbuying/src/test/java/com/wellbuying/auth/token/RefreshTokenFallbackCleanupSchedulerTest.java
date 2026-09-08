package com.wellbuying.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.auth.jwt.JwtProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenFallbackCleanupSchedulerTest {

    @Mock
    private RefreshTokenFallbackJpaRepository refreshTokenFallbackJpaRepository;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenFallbackCleanupScheduler scheduler;

    @Captor
    private ArgumentCaptor<Long> cutoffCaptor;

    // 단일 벌크 DELETE라 MemberDormancyScheduler와 달리 반복 호출 없이 1회만 실행되는지, cutoff가
    // now - refreshTokenExpirationMs(초 단위)로 정확히 계산되는지 검증
    @Test
    void refreshTokenExpirationMs만큼_이전_시점을_cutoff로_계산해_한_번만_삭제_쿼리를_호출한다() {
        when(jwtProperties.refreshTokenExpirationMs()).thenReturn(604_800_000L); // 7일
        when(refreshTokenFallbackJpaRepository.deleteExpiredBefore(anyLong())).thenReturn(0);
        long before = Instant.now().getEpochSecond() - 604_800L;

        scheduler.cleanupExpiredFallbackSessions();

        long after = Instant.now().getEpochSecond() - 604_800L;
        verify(refreshTokenFallbackJpaRepository, times(1)).deleteExpiredBefore(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isBetween(before, after);
    }
}
