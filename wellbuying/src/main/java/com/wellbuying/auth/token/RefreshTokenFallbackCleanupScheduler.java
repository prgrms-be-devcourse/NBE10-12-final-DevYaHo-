package com.wellbuying.auth.token;

import com.wellbuying.auth.jwt.JwtProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Redis 장애 중 생성된 뒤 방치된 refresh_token_fallback 행을 매일 정리하는 배치(phase23 §2).
// MemberDormancyScheduler(00:05)와 겹치지 않도록 00:15에 실행
@Component
public class RefreshTokenFallbackCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenFallbackCleanupScheduler.class);

    private final RefreshTokenFallbackJpaRepository refreshTokenFallbackJpaRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenFallbackCleanupScheduler(RefreshTokenFallbackJpaRepository refreshTokenFallbackJpaRepository,
            JwtProperties jwtProperties) {
        this.refreshTokenFallbackJpaRepository = refreshTokenFallbackJpaRepository;
        this.jwtProperties = jwtProperties;
    }

    // deleteExpiredBefore()가 @Modifying 벌크 쿼리라 활성 트랜잭션이 반드시 필요함(JPA
    // executeUpdate() 요구사항) - 리포지토리 프록시가 자동으로 열어주지 않아 호출부에 명시해야 한다
    @Transactional
    @Scheduled(cron = "0 15 0 * * *")
    public void cleanupExpiredFallbackSessions() {
        long cutoffEpochSeconds = Instant.now().minusMillis(jwtProperties.refreshTokenExpirationMs()).getEpochSecond();
        int deletedCount = refreshTokenFallbackJpaRepository.deleteExpiredBefore(cutoffEpochSeconds);
        if (deletedCount > 0) {
            log.info("refresh token fallback 정리 배치 완료 - {}건 삭제", deletedCount);
        } else {
            log.debug("refresh token fallback 정리 배치 완료 - 삭제 대상 없음");
        }
    }
}
