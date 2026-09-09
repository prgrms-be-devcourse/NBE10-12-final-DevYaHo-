package com.wellbuying.domain.member.dto;

import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.seller.entity.SellerStatus;
import java.time.LocalDateTime;

// 관리자 회원 목록 조회 응답 DTO - MemberQueryRepositoryImpl에서 QueryDSL Projections.constructor로 직접 조립
// sellerId/sellerStatus는 role=SELLER인 회원만 값이 채워짐(SELLER_INFO left join) - 그 외에는 null
public record MemberSummaryResponse(Long id, String email, String name, Role role, MemberStatus status,
        String phoneNumber, LocalDateTime createdAt, Long sellerId, SellerStatus sellerStatus) {
}
