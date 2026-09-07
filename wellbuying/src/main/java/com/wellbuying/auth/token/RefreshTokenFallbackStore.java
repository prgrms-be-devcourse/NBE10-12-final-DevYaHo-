package com.wellbuying.auth.token;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Redis 서킷이 open일 때만 쓰이는 DB 폴백 저장소 접근 전담 - 항상 REQUIRES_NEW로 동작해
// 호출부(reissue()의 readOnly 트랜잭션 등)의 트랜잭션 설정과 무관하게 쓰기/락을 보장한다 (phase21 §2-3)
@Component
public class RefreshTokenFallbackStore {

    private final RefreshTokenFallbackJpaRepository repository;

    public RefreshTokenFallbackStore(RefreshTokenFallbackJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long memberId, String deviceId, RefreshTokenValue value) {
        RefreshTokenFallbackEntity entity = repository.findByMemberIdAndDeviceId(memberId, deviceId)
                .orElseGet(() -> repository.save(RefreshTokenFallbackEntity.create(memberId, deviceId, value)));
        entity.applyValue(value);
    }

    // rotate_refresh_token.lua와 동일한 규칙으로 검증 후 회전한다 (1=성공, 0=세션없음, -1=재사용감지)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RotateResult rotate(Long memberId, String deviceId, String oldTokenHash, String newTokenHash,
            long graceSeconds) {
        Optional<RefreshTokenFallbackEntity> found = repository.findByMemberIdAndDeviceIdForUpdate(memberId,
                deviceId);
        if (found.isEmpty()) {
            return RotateResult.notFound();
        }
        RefreshTokenFallbackEntity entity = found.get();
        long now = Instant.now().getEpochSecond();
        if (!entity.matches(oldTokenHash, now)) {
            // 진짜 탈취 의심 - lua와 동일하게 세션을 폐기한다 (DB 폴백에 반영된 범위 내에서)
            repository.deleteByMemberId(memberId);
            return RotateResult.reuseDetected();
        }
        RefreshTokenValue rotated = new RefreshTokenValue(newTokenHash, entity.getTokenHash(),
                now + graceSeconds, now, now);
        entity.applyValue(rotated);
        return RotateResult.success(rotated);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(Long memberId, String deviceId) {
        repository.deleteByMemberIdAndDeviceId(memberId, deviceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteAll(Long memberId) {
        repository.deleteByMemberId(memberId);
    }

    public record RotateResult(long code, RefreshTokenValue value) {
        static RotateResult notFound() {
            return new RotateResult(0, null);
        }

        static RotateResult reuseDetected() {
            return new RotateResult(-1, null);
        }

        static RotateResult success(RefreshTokenValue value) {
            return new RotateResult(1, value);
        }
    }
}
