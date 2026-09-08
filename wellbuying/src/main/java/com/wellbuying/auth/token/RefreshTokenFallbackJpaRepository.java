package com.wellbuying.auth.token;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenFallbackJpaRepository extends JpaRepository<RefreshTokenFallbackEntity, Long> {

    Optional<RefreshTokenFallbackEntity> findByMemberIdAndDeviceId(Long memberId, String deviceId);

    // 동시 rotate 요청 간 경쟁을 막기 위한 비관적 락 - Redis Lua 스크립트의 원자성을 DB에서 근사한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshTokenFallbackEntity r where r.memberId = :memberId and r.deviceId = :deviceId")
    Optional<RefreshTokenFallbackEntity> findByMemberIdAndDeviceIdForUpdate(
            @Param("memberId") Long memberId, @Param("deviceId") String deviceId);

    // 파생 delete 쿼리(deleteBy...)는 대상을 SELECT로 조회한 뒤 건별로 DELETE하므로, 단일 벌크 DELETE로 대체
    // flushAutomatically = true: bulk delete 실행 전에 영속성 컨텍스트의 pending 변경(예: resetPassword()의
    // 비밀번호 변경)을 먼저 flush한 뒤 clear하므로, ambient 트랜잭션 안에서 REQUIRED로 합류해도 caller의
    // 미반영 변경이 유실되지 않는다(phase21 §4-6)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshTokenFallbackEntity r where r.memberId = :memberId and r.deviceId = :deviceId")
    void deleteByMemberIdAndDeviceId(@Param("memberId") Long memberId, @Param("deviceId") String deviceId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshTokenFallbackEntity r where r.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);

    // Redis 장애 중 생성된 뒤 재조회/로그아웃 없이 방치된 폴백 세션 정리(phase23 §2) - Redis Hash TTL과
    // 달리 이 테이블은 시간 기반 자동 만료가 없어 RefreshTokenFallbackCleanupScheduler가 주기적으로 호출한다.
    // 대상이 평상시 0건에 가까울 것으로 예상돼(장애 중에만 생성됨) LIMIT 기반 배치 분할 없이 단일 벌크 DELETE로 처리
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshTokenFallbackEntity r where r.lastUsedAt < :cutoffEpochSeconds")
    int deleteExpiredBefore(@Param("cutoffEpochSeconds") long cutoffEpochSeconds);
}
