package com.wellbuying.domain.payment.event;

import java.time.LocalDateTime;

// 결제 실패 - notification이 구독한다 (정산 대상이 아니므로 settlement는 구독하지 않음)
public record PaymentFailedEvent(
        String eventType,
        Long paymentId,
        Long groupBuyId,
        Long groupBuyParticipantId,
        Long memberId,
        int amount,
        String reason,
        LocalDateTime occurredAt
) {

    public static PaymentFailedEvent of(Long paymentId, Long groupBuyId, Long groupBuyParticipantId, Long memberId,
            int amount, String reason) {
        return new PaymentFailedEvent(
                PaymentEventType.PAYMENT_FAILED.code(),
                paymentId,
                groupBuyId,
                groupBuyParticipantId,
                memberId,
                amount,
                reason,
                LocalDateTime.now());
    }
}
