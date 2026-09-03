package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.product.entity.Product;
import java.time.LocalDateTime;

public record GroupBuySummaryResponse(
        Long id,
        Long productId,
        String productName,
        String productCategory,
        Long producerId,
        String title,
        GroupBuyStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int currentQuantity,
        int maxQuantity,
        boolean suspended
) {

    // product가 null이면(이론상 항상 존재하지만, 이 응답이 추가되기 전에 만들어진 레거시 행 대비) 빈 값으로 안전하게 처리
    public static GroupBuySummaryResponse of(GroupBuy groupBuy, Product product, String categoryName) {
        return new GroupBuySummaryResponse(
                groupBuy.getId(),
                groupBuy.getProductId(),
                product != null ? product.getProductName() : "",
                categoryName,
                groupBuy.getProducerId(),
                groupBuy.getTitle(),
                groupBuy.getStatus(),
                groupBuy.getStartAt(),
                groupBuy.getEndAt(),
                groupBuy.getCurrentQuantity(),
                groupBuy.getMaxQuantity(),
                groupBuy.isSuspended());
    }
}
