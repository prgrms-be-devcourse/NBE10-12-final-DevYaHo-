package com.wellbuying.domain.member.dto;

import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.Role;
import java.time.LocalDateTime;

// 관리자 회원 목록 조회 응답 DTO - MemberQueryRepositoryImpl에서 QueryDSL Projections.constructor로 직접 조립
public record MemberSummaryResponse(Long id, String email, String name, Role role, MemberStatus status,
        String phoneNumber, LocalDateTime createdAt) {
}
