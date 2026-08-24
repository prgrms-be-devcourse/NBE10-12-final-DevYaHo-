package com.wellbuying.groupbuy.dto;

import com.wellbuying.groupbuy.domain.GroupBuy;
import com.wellbuying.groupbuy.domain.GroupBuyPrice;
import com.wellbuying.groupbuy.domain.GroupBuyStatus;
import java.time.LocalDateTime;
import java.util.List;

// 상세 조회 - 잘 안 바뀌는 정보(상품/생산자/가격구간 등)만 담는다. 실시간으로 바뀌는 값은 GroupBuyStatusResponse 참고
public record GroupBuyDetailResponse(
        Long id,
        Long productId,
        Long producerId,
        String title,
        GroupBuyStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int minQuantity,
        int maxQuantity,
        List<GroupBuyPriceResponse> priceTiers,
        LocalDateTime createdAt
) {

    public static GroupBuyDetailResponse of(GroupBuy groupBuy, List<GroupBuyPrice> priceTiers) {
        return new GroupBuyDetailResponse(
                groupBuy.getId(),
                groupBuy.getProductId(),
                groupBuy.getProducerId(),
                groupBuy.getTitle(),
                groupBuy.getStatus(),
                groupBuy.getStartAt(),
                groupBuy.getEndAt(),
                groupBuy.getMinQuantity(),
                groupBuy.getMaxQuantity(),
                priceTiers.stream().map(GroupBuyPriceResponse::of).toList(),
                groupBuy.getCreatedAt());
    }
}
