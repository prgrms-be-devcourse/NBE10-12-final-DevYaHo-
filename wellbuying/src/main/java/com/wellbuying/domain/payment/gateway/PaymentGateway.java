package com.wellbuying.domain.payment.gateway;

// PG 연동 경계. 구현체를 갈아끼울 수 있게 인터페이스로 두고, 호출은 DB 트랜잭션 밖에서만 한다
// (외부 호출이 트랜잭션 안에 있으면 응답이 늦어질 때 커넥션을 그만큼 붙잡고 있게 되므로)
public interface PaymentGateway {

    String provider();

    // 승인 거절(응답 받음) 시 PgApprovalException을, 재시도 소진까지 응답을 못 받으면
    // PgApprovalTimeoutException(PgApprovalException의 하위 타입)을 던진다 (09-pg-timeout-retry.md)
    PgApproveResult approve(PgApproveCommand command);

    // 결제조회 - orderId 기준. paymentKey를 못 받은 채로 승인 여부가 불명확해진 건(UNCONFIRMED)을
    // 확인할 때 쓴다. 호출 자체가 실패하면 PgInquiryException
    PgInquiryResult inquire(String orderId);
}
