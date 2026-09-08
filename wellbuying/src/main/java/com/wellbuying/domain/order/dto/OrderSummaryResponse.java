package com.wellbuying.domain.order.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.entity.OrderStatus;
import com.wellbuying.domain.product.entity.Product;
import java.time.LocalDateTime;

// 결제/주문 내역 목록의 한 줄. 상품명·수량·공구 제목은 다른 도메인 행을 배치 조회해 조합한다
// (Order에 스냅샷으로 복사하지 않는다 - hs-docs/order/01-order-history.md)
public record OrderSummaryResponse(
        String orderId,
        Long groupBuyId,
        String groupBuyTitle,
        String productName,
        String thumbnailUrl,
        int quantity,
        int totalPrice,
        OrderStatus status,
        LocalDateTime createdAt
) {

    // part/groupBuy/product는 이론상 항상 존재하지만, 하나라도 없으면 빈 값으로 안전하게 처리한다
    // (GroupBuySummaryResponse.of와 같은 방어 정책)
    public static OrderSummaryResponse of(Order order, GroupBuyPart part, GroupBuy groupBuy, Product product) {
        return new OrderSummaryResponse(
                order.getOrderId(),
                groupBuy != null ? groupBuy.getId() : null,
                groupBuy != null ? groupBuy.getTitle() : "",
                product != null ? product.getProductName() : "",
                product != null ? product.getThumbnailUrl() : null,
                part != null ? part.getQuantity() : 0,
                order.getTotalPrice(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
