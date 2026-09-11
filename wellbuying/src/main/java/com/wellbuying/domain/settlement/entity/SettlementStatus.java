package com.wellbuying.domain.settlement.entity;

public enum SettlementStatus {

    // 정산액이 확정됐고 판매자 지급을 기다리는 상태 (Phase 2가 만드는 유일한 상태)
    CONFIRMED,
    // 판매자 계좌로 실제 지급이 완료된 상태 (지급 실행 연동 시 도입 - 아직 미사용)
    PAID
}
