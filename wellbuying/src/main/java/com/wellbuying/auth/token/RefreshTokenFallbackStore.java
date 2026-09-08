package com.wellbuying.auth.token;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Redis 서킷이 open일 때만 쓰이는 DB 폴백 저장소 접근 전담.
// 모든 메서드가 기본 propagation(REQUIRED)을 쓴다 - save()/rotate()는 호출부(login()/reissue())가 이미 DB
// 조회 구간과 Redis 호출 구간을 분리해 ambient 트랜잭션이 없는 상태로만 호출되고(phase21 §4-3),
// delete()/deleteAll()은 파생 delete 쿼리(RefreshTokenFallbackJpaRepository)가
// flushAutomatically = true라 REQUIRED로 합류해도 clear() 전에 caller의 pending 변경(예: resetPassword()의
// 비밀번호 변경)을 먼저 flush하므로 유실되지 않는다(§4-6, 과거엔 REQUIRES_NEW로 격리했었음).
@Component
public class RefreshTokenFallbackStore {

    private final RefreshTokenFallbackJpaRepository repository;

    public RefreshTokenFallbackStore(RefreshTokenFallbackJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(Long memberId, String deviceId, RefreshTokenValue value) {
        RefreshTokenFallbackEntity entity = repository.findByMemberIdAndDeviceId(memberId, deviceId)
                .orElseGet(() -> repository.save(RefreshTokenFallbackEntity.create(memberId, deviceId, value)));
        entity.applyValue(value);
    }

    // rotate_refresh_token.lua와 동일한 규칙으로 검증 후 회전한다 (1=성공, 0=세션없음, -1=재사용감지)
    @Transactional
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

    @Transactional
    public void delete(Long memberId, String deviceId) {
        repository.deleteByMemberIdAndDeviceId(memberId, deviceId);
    }

    @Transactional
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
