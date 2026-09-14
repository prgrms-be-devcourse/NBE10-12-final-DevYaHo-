package com.wellbuying.domain.settlement.entity;

public enum SettlementItemStatus {

    // 결제 완료 이벤트를 받아 적립만 된 상태 (Phase 1)
    ACCRUED,
    // 유예기간이 끝나 배치가 정산을 확정한 상태 (Phase 2)
    CONFIRMED
}
