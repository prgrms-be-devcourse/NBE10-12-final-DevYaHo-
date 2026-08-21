package com.wellbuying.member.repository;

import com.wellbuying.member.domain.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 이메일 중복 가입 여부 확인
    boolean existsByEmail(String email);

    // 탈퇴하지 않은(deletedAt이 null인) 회원을 이메일로 조회
    Optional<Member> findByEmailAndDeletedAtIsNull(String email);

    // 탈퇴하지 않은(deletedAt이 null인) 회원을 id로 조회 - 토큰 재발급 시 최신 role 확인용
    Optional<Member> findByIdAndDeletedAtIsNull(Long id);
}
