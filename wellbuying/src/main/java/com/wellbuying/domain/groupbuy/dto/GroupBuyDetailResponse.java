package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.product.entity.Product;
import java.time.LocalDateTime;
import java.util.List;

// 상세 조회 - 잘 안 바뀌는 정보(상품/생산자/가격구간 등)만 담는다. 실시간으로 바뀌는 값은 GroupBuyStatusResponse 참고
public record GroupBuyDetailResponse(
        Long id,
        Long productId,
        String productName,
        String productCategory,
        Long producerId,
        String title,
        GroupBuyStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        int minQuantity,
        int maxQuantity,
        List<GroupBuyPriceResponse> priceTiers,
        LocalDateTime createdAt,
        boolean suspended
) {

    // product가 null이면(이론상 항상 존재하지만, 이 응답이 추가되기 전에 만들어진 레거시 행 대비) 빈 값으로 안전하게 처리
    public static GroupBuyDetailResponse of(GroupBuy groupBuy, List<GroupBuyPrice> priceTiers, Product product,
            String categoryName) {
        return new GroupBuyDetailResponse(
                groupBuy.getId(),
                groupBuy.getProductId(),
                product != null ? product.getProductName() : "",
                categoryName,
                groupBuy.getProducerId(),
                groupBuy.getTitle(),
                groupBuy.getStatus(),
                groupBuy.getStartAt(),
                groupBuy.getEndAt(),
                groupBuy.getMinQuantity(),
                groupBuy.getMaxQuantity(),
                priceTiers.stream().map(GroupBuyPriceResponse::of).toList(),
                groupBuy.getCreatedAt(),
                groupBuy.isSuspended());
    }
}
