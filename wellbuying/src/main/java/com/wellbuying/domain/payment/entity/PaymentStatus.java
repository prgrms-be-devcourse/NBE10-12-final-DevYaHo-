package com.wellbuying.domain.payment.entity;

public enum PaymentStatus {

    READY,
    APPROVED,
    CANCELED,
    FAILED,
    REFUNDED,
    // PG 승인 타임아웃/5xx 재시도가 소진돼 실제 승인 여부를 모르는 과도 상태.
    // PaymentUnconfirmedReconciliationJob이 결제조회로 확정할 때까지만 머무른다 (09-pg-timeout-retry.md)
    UNCONFIRMED
}
