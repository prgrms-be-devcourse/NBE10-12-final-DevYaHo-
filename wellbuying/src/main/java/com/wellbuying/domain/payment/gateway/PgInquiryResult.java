package com.wellbuying.domain.payment.gateway;

import java.time.LocalDateTime;

// 결제조회(GET /v1/payments/orders/{orderId}) 응답. status는 토스 Payment 객체의 상태값 그대로
// (DONE/ABORTED/EXPIRED/CANCELED/...), 토스에 접수조차 안 된 건은 "NOT_FOUND_PAYMENT"로 정규화해서 담는다
// (토스 원 응답은 이 경우 에러코드라 정상 상태값이 없다) - 09-pg-timeout-retry.md
public record PgInquiryResult(String status, String paymentKey, LocalDateTime approvedAt) {

    private static final String STATUS_DONE = "DONE";

    public boolean isApproved() {
        return STATUS_DONE.equals(status);
    }

    public PgApproveResult toApproveResult() {
        return new PgApproveResult(paymentKey, approvedAt);
    }
}
