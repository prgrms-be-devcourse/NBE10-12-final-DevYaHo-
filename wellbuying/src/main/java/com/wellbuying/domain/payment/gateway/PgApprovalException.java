package com.wellbuying.domain.payment.gateway;

// PG가 승인을 거절했거나 승인 호출 자체가 실패한 경우.
// 이 예외가 나면 결제는 이뤄지지 않은 것으로 보고 Payment를 FAILED로 전이시킨다.
// 주의: 타임아웃처럼 "승인이 됐는지 알 수 없는" 응답도 여기로 들어오므로,
// 실제로는 승인됐는데 FAILED로 남는 경우가 생길 수 있다 (01-consumer.md의 알려진 리스크)
public class PgApprovalException extends RuntimeException {

    public PgApprovalException(String message) {
        super(message);
    }

    public PgApprovalException(String message, Throwable cause) {
        super(message, cause);
    }
}
