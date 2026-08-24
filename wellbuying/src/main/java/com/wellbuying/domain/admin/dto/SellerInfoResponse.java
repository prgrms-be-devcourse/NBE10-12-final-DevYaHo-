package com.wellbuying.domain.admin.dto;

import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.entity.SellerStatus;
import java.time.LocalDateTime;

public record SellerInfoResponse(Long id, Long memberId, SellerStatus status, String bankName, String companyName,
        LocalDateTime createdAt) {

    // SellerInfo 엔티티를 관리자 응답 DTO로 변환 - 엔티티를 그대로 노출하지 않음
    public static SellerInfoResponse from(SellerInfo sellerInfo) {
        return new SellerInfoResponse(sellerInfo.getId(), sellerInfo.getMemberId(), sellerInfo.getStatus(),
                sellerInfo.getBankName(), sellerInfo.getCompanyName(), sellerInfo.getCreatedAt());
    }
}
