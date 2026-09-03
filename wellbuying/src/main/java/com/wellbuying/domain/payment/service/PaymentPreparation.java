package com.wellbuying.domain.payment.service;

// PG 승인을 시도하기 직전까지 준비된 상태 (TX1의 결과)
// failed=true면 승인을 시도하지 않고 곧바로 실패 처리한다 - 돈이 나가기 전에 걸러낸 경우다.
// orderId는 TX1에서 만든 PENDING 주문의 식별자이자 토스에 보낼 orderId이며, 승인을 아예
// 시도하지 않는 실패 경로에서는 주문을 만들지 않으므로 null이다
public record PaymentPreparation(
        Long paymentId,
        String orderId,
        boolean failed,
        String failureReason
) {

    public static PaymentPreparation ready(Long paymentId, String orderId) {
        return new PaymentPreparation(paymentId, orderId, false, null);
    }

    public static PaymentPreparation failed(Long paymentId, String reason) {
        return new PaymentPreparation(paymentId, null, true, reason);
    }
}
