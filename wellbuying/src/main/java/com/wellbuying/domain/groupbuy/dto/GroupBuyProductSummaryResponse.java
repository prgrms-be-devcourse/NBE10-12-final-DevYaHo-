package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;

// 상품 검색 색인(OpenSearch)에 함께 노출할 "진행 중인 공동구매" 요약 - GroupBuyService.getActiveSummariesByProductIds 응답
public record GroupBuyProductSummaryResponse(
        GroupBuyStatus groupBuyStatus,
        int currentUnitPrice,
        int participantCount,
        int targetQuantity
) {
    public static GroupBuyProductSummaryResponse of(GroupBuy groupBuy, int currentUnitPrice) {
        return new GroupBuyProductSummaryResponse(
                groupBuy.getStatus(),
                currentUnitPrice,
                groupBuy.getCurrentQuantity(),
                groupBuy.getMinQuantity()
        );
    }
}
