package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.domain.GroupBuy;
import com.wellbuying.domain.groupbuy.domain.GroupBuyStatus;
import java.time.LocalDateTime;

public record GroupBuySummaryResponse(
        Long id,
        Long productId,
        Long producerId,
        String title,
        GroupBuyStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int currentQuantity,
        int maxQuantity
) {

    public static GroupBuySummaryResponse of(GroupBuy groupBuy) {
        return new GroupBuySummaryResponse(
                groupBuy.getId(),
                groupBuy.getProductId(),
                groupBuy.getProducerId(),
                groupBuy.getTitle(),
                groupBuy.getStatus(),
                groupBuy.getStartAt(),
                groupBuy.getEndAt(),
                groupBuy.getCurrentQuantity(),
                groupBuy.getMaxQuantity());
    }
}
