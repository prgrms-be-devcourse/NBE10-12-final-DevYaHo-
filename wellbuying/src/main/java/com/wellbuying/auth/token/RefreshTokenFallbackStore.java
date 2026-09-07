package com.wellbuying.auth.token;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Redis 서킷이 open일 때만 쓰이는 DB 폴백 저장소 접근 전담.
// save()/rotate()는 호출부(login()/reissue())가 이미 DB 조회 구간과 Redis 호출 구간을 분리해, 이 저장소를
// 호출할 때는 ambient 트랜잭션이 없는 상태이므로 기본 propagation(REQUIRED)만으로도 매번 새 트랜잭션을 여는 것과
// 동일하다 (phase21 §4-3). delete()/deleteAll()은 resetPassword()의 @Transactional 안에서 logoutAll()을 거쳐
// ambient 트랜잭션이 있는 채로도 호출되는데, 파생 delete 쿼리가 @Modifying(clearAutomatically = true)라 REQUIRED로
// 합류하면 그 트랜잭션의 영속성 컨텍스트 전체를 clear()해 caller가 방금 변경한(아직 flush 전인) 엔티티까지 통째로
// 날려버린다 - 그래서 이 둘은 REQUIRES_NEW로 격리해 별도 영속성 컨텍스트에서만 clear가 일어나게 유지한다.
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
