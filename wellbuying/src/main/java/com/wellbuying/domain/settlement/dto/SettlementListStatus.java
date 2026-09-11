package com.wellbuying.domain.settlement.dto;

// 판매자 정산 목록의 필터/표시 상태 - 딱 두 가지.
// PENDING: 아직 확정 안 된 공동구매 (settlement_item이 ACCRUED로 남아있고 settlement 행이 아직 없음)
// COMPLETED: 정산이 확정된 공동구매 (settlement 행 존재 - CONFIRMED/PAID를 구분하지 않고 둘 다 "완료"로 취급.
//            지급 실행 전/후 구분이 필요해지면 그때 세분화한다)
public enum SettlementListStatus {
    PENDING,
    COMPLETED
}
