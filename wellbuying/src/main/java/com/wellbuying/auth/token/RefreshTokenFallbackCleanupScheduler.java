package com.wellbuying.auth.token;

import com.wellbuying.auth.jwt.JwtProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Redis 장애 중 생성된 뒤 방치된 refresh_token_fallback 행을 매일 정리하는 배치(phase23 §2).
// MemberDormancyScheduler(00:05)와 겹치지 않도록 00:15에 실행
@Component
public class RefreshTokenFallbackCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenFallbackCleanupScheduler.class);

    private final RefreshTokenFallbackStore refreshTokenFallbackStore;
    private final JwtProperties jwtProperties;

    public RefreshTokenFallbackCleanupScheduler(RefreshTokenFallbackStore refreshTokenFallbackStore,
            JwtProperties jwtProperties) {
        this.refreshTokenFallbackStore = refreshTokenFallbackStore;
        this.jwtProperties = jwtProperties;
    }

    // 트랜잭션 경계는 RefreshTokenFallbackStore.deleteExpiredBefore()가 담당한다 - 이 메서드 자체는
    // 트랜잭션 없이 try/catch로 로깅만 하므로, @Transactional 메서드 안에서 예외를 삼켜 프록시가
    // 잘못 커밋을 시도하는 문제(UnexpectedRollbackException)가 생기지 않는다
    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Seoul")
    public void cleanupExpiredFallbackSessions() {
        try {
            long cutoffEpochSeconds = Instant.now().minusMillis(jwtProperties.refreshTokenExpirationMs())
                    .getEpochSecond();
            int deletedCount = refreshTokenFallbackStore.deleteExpiredBefore(cutoffEpochSeconds);
            if (deletedCount > 0) {
                log.info("refresh token fallback 정리 배치 완료 - {}건 삭제", deletedCount);
            } else {
                log.debug("refresh token fallback 정리 배치 완료 - 삭제 대상 없음");
            }
        } catch (Exception e) {
            log.error("refresh token fallback 정리 배치 중 오류 발생", e);
        }
    }
}
