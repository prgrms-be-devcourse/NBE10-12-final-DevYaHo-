package com.wellbuying.domain.seller.dto;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;

public record SellerSignupResponse(Long memberId, String email, String name, Role role) {

    // Member 엔티티를 판매자 다이렉트 가입 응답 DTO로 변환
    public static SellerSignupResponse from(Member member) {
        return new SellerSignupResponse(member.getId(), member.getEmail(), member.getName(), member.getRole());
    }
}
