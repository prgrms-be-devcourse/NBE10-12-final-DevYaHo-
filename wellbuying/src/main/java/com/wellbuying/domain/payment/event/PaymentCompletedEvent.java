package com.wellbuying.domain.payment.event;

import com.wellbuying.domain.payment.entity.Order;
import java.time.LocalDateTime;

// 결제 승인 성공 - notification(구매 완료 안내), settlement(정산 대상 적재)이 구독한다
public record PaymentCompletedEvent(
        String eventType,
        Long paymentId,
        Long orderId,
        Long groupBuyId,
        Long groupBuyParticipantId,
        Long memberId,
        Long producerId,
        int amount,
        String pgTransactionId,
        LocalDateTime occurredAt
) {

    // Order에 결제 식별자와 금액이 모두 들어있어 Payment를 다시 조회하지 않는다
    public static PaymentCompletedEvent of(Order order, Long groupBuyId, Long producerId, String pgTransactionId) {
        return new PaymentCompletedEvent(
                PaymentEventType.PAYMENT_COMPLETED.code(),
                order.getPaymentId(),
                order.getId(),
                groupBuyId,
                order.getGroupBuyParticipantId(),
                order.getMemberId(),
                producerId,
                order.getTotalPrice(),
                pgTransactionId,
                LocalDateTime.now());
    }
}
