package com.wellbuying.member.dto;

import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.Role;

public record MemberResponse(Long memberId, String email, String name, String profileImageUrl, Role role) {

    // Member 엔티티를 내 정보 조회 응답 DTO로 변환
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getProfileImage(),
                member.getRole());
    }
}
