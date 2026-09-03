package com.wellbuying.domain.payment.entity;

// PG 승인은 성공했으나 그 결과를 DB에 남기지 못한 상황의 구분값 (수동 대사 시 원인 파악용)
public enum PaymentFailureType {

    // Payment를 APPROVED로 전이시키는 트랜잭션이 커밋되지 못함
    APPROVE_RESULT_PERSIST_FAILED,
    // Payment는 APPROVED가 됐으나 Order 반영(PENDING → PAID)에서 실패
    ORDER_CREATE_FAILED
}
