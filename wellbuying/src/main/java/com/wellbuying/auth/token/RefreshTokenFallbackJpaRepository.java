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
    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshTokenFallbackEntity r where r.memberId = :memberId and r.deviceId = :deviceId")
    void deleteByMemberIdAndDeviceId(@Param("memberId") Long memberId, @Param("deviceId") String deviceId);

    @Modifying(clearAutomatically = true)
    @Query("delete from RefreshTokenFallbackEntity r where r.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
