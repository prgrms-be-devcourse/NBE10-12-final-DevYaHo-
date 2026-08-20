package com.wellbuying.member.dto;

import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.Role;

public record SignupResponse(Long memberId, String email, String name, Role role) {

    // Member 엔티티를 회원가입 응답 DTO로 변환
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }
}
