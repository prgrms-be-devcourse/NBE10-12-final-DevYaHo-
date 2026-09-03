package com.wellbuying.domain.payment.gateway;

// PG 승인 요청에 필요한 값 묶음
// idempotencyKey는 PG 측 중복 승인 방지용이며, 이벤트를 재수신해도 같은 값이어야 의미가 있다
public record PgApproveCommand(
        String billingKey,
        String customerKey,
        String orderId,
        String orderName,
        int amount,
        String idempotencyKey
) {
}
