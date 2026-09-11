package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import java.time.LocalDateTime;

// 상품 검색 색인(OpenSearch)에 함께 노출할 "진행 중인 공동구매" 요약 - GroupBuyService.getActiveSummariesByProductIds 응답
public record GroupBuyProductSummaryResponse(
        Long groupBuyId,
        String title,
        GroupBuyStatus groupBuyStatus,
        int currentUnitPrice,
        int currentQuantity,
        int targetQuantity,
        int maxQuantity,
        LocalDateTime endAt
) {
    public static GroupBuyProductSummaryResponse of(GroupBuy groupBuy, int currentUnitPrice) {
        return new GroupBuyProductSummaryResponse(
                groupBuy.getId(),
                groupBuy.getTitle(),
                groupBuy.getStatus(),
                currentUnitPrice,
                groupBuy.getCurrentQuantity(),
                groupBuy.getMinQuantity(),
                groupBuy.getMaxQuantity(),
                groupBuy.getEndAt()
        );
    }
}
