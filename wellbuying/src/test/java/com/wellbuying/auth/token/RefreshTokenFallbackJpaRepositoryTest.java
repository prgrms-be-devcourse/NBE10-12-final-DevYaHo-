package com.wellbuying.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RefreshTokenFallbackJpaRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenFallbackJpaRepository refreshTokenFallbackJpaRepository;

    // cutoff보다 먼저 사용된(lastUsedAt이 더 과거인) 행만 삭제되고, cutoff 이후에 사용된 행은 남아있는지 검증(phase23 §2)
    @Test
    void cutoff_이전에_마지막_사용된_행만_삭제한다() {
        long now = Instant.now().getEpochSecond();
        long memberId = System.nanoTime();
        refreshTokenFallbackJpaRepository.save(RefreshTokenFallbackEntity.create(memberId, "expired-device",
                new RefreshTokenValue("hash-expired", null, null, now - 1000, now - 1000)));
        refreshTokenFallbackJpaRepository.save(RefreshTokenFallbackEntity.create(memberId, "active-device",
                new RefreshTokenValue("hash-active", null, null, now, now)));

        int deletedCount = refreshTokenFallbackJpaRepository.deleteExpiredBefore(now - 500);

        assertThat(deletedCount).isEqualTo(1);
        assertThat(refreshTokenFallbackJpaRepository.findByMemberIdAndDeviceId(memberId, "expired-device"))
                .isEmpty();
        assertThat(refreshTokenFallbackJpaRepository.findByMemberIdAndDeviceId(memberId, "active-device"))
                .isPresent();
    }
}
