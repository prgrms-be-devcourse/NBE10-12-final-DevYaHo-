package com.wellbuying.domain.member.dto;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;

public record SignupResponse(Long memberId, String email, String name, Role role) {

    // Member 엔티티를 회원가입 응답 DTO로 변환
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }
}
