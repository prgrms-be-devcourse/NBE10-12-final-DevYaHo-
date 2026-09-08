package com.wellbuying.domain.order.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.entity.OrderStatus;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.entity.PaymentStatus;
import com.wellbuying.domain.product.entity.Product;
import java.time.LocalDateTime;

// 주문 1건의 결제 정보 상세 - 구매한 물품, 갯수, 단가, 총 결제 금액, 결제 수단, 배송지, 상태
public record OrderDetailResponse(
        String orderId,
        Long groupBuyId,
        String groupBuyTitle,
        String productName,
        String thumbnailUrl,
        int quantity,
        // group_buy_part.applied_price - 결제된 건이면 항상 채워져 있으나, 이론상 성사 전 상태 대비 nullable
        Integer unitPrice,
        int totalPrice,
        OrderStatus status,
        String shippingAddress,
        String pgProvider,
        // PG 승인 전에는 null
        String pgTransactionId,
        PaymentStatus paymentStatus,
        LocalDateTime approvedAt,
        LocalDateTime createdAt
) {

    public static OrderDetailResponse of(Order order, GroupBuyPart part, GroupBuy groupBuy, Product product,
            Payment payment) {
        return new OrderDetailResponse(
                order.getOrderId(),
                groupBuy != null ? groupBuy.getId() : null,
                groupBuy != null ? groupBuy.getTitle() : "",
                product != null ? product.getProductName() : "",
                product != null ? product.getThumbnailUrl() : null,
                part != null ? part.getQuantity() : 0,
                part != null ? part.getAppliedPrice() : null,
                order.getTotalPrice(),
                order.getStatus(),
                order.getShippingAddress(),
                payment != null ? payment.getPgProvider() : null,
                payment != null ? payment.getPgTransactionId() : null,
                payment != null ? payment.getStatus() : null,
                payment != null ? payment.getApprovedAt() : null,
                order.getCreatedAt());
    }
}
