package com.wellbuying.domain.member.repository;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberQueryRepository {

    // 이메일 중복 가입 여부 확인
    boolean existsByEmail(String email);

    // 탈퇴하지 않은(deletedAt이 null인) 회원을 이메일로 조회
    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    // 탈퇴하지 않은(deletedAt이 null인) 회원을 id로 조회 - 토큰 재발급 시 최신 role 확인용
    Optional<Member> findByIdAndDeletedAtIsNull(Long id);

    // 휴면 전환 배치 대상 조회 - status가 ACTIVE이면서 lastLoginAt이 threshold 이전인 회원을 limit만큼 조회
    List<Member> findByStatusAndLastLoginAtBefore(MemberStatus status, LocalDateTime threshold, Limit limit);
}
