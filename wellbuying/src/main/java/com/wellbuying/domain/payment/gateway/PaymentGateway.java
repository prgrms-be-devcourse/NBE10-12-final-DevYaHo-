package com.wellbuying.domain.payment.gateway;

// PG 연동 경계. 구현체를 갈아끼울 수 있게 인터페이스로 두고, 호출은 DB 트랜잭션 밖에서만 한다
// (외부 호출이 트랜잭션 안에 있으면 응답이 늦어질 때 커넥션을 그만큼 붙잡고 있게 되므로)
public interface PaymentGateway {

    String provider();

    // 승인 실패 시 PgApprovalException을 던진다
    PgApproveResult approve(PgApproveCommand command);
}
