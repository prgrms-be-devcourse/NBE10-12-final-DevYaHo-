package com.wellbuying.domain.member.repository;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberQueryRepository {

    // 이메일 중복 가입 여부 확인
    boolean existsByEmail(String email);

    // 탈퇴하지 않은(deletedAt이 null인) 회원을 이메일로 조회
    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    // 탈퇴하지 않은(deletedAt이 null인) 회원을 id로 조회 - 토큰 재발급 시 최신 role 확인용
    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    // 휴면 전환 배치 대상 조회 - status가 ACTIVE이면서 lastLoginAt이 threshold 이전인(가입 후 미접속 회원은 createdAt 기준) 회원의 id를 limit 건까지 조회
    @Query("SELECT m.id FROM Member m "
            + "WHERE m.status = :activeStatus "
            + "AND ((m.lastLoginAt IS NOT NULL AND m.lastLoginAt < :threshold) "
            + "  OR (m.lastLoginAt IS NULL AND m.createdAt < :threshold))")
    List<Long> findIdsForDormancy(@Param("activeStatus") MemberStatus activeStatus,
            @Param("threshold") LocalDateTime threshold, Limit limit);

    // 위에서 조회한 id 목록을 벌크 UPDATE로 DORMANT 전환
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Member m SET m.status = :dormantStatus WHERE m.id IN :ids")
    int bulkMarkDormantByIds(@Param("dormantStatus") MemberStatus dormantStatus, @Param("ids") List<Long> ids);
}
