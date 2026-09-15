package com.wellbuying.domain.payment.gateway;

// PG 승인 응답을 끝내 받지 못한 경우(타임아웃/5xx가 재시도 소진까지 반복됨) - 실제로 승인됐는지 알 수 없다.
// PgApprovalException(응답을 받은 명확한 거절)과 구분해서, 호출자가 Payment를 FAILED가 아니라
// UNCONFIRMED로 남기게 한다 (09-pg-timeout-retry.md)
public class PgApprovalTimeoutException extends PgApprovalException {

    public PgApprovalTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
