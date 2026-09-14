package com.wellbuying.domain.settlement.dto;

// 관리자 정산 대시보드 상단 요약 카드. pendingCount/pendingAmount는 settlement_item(ACCRUED) 기준(정산 확정
// 전이라 Settlement 행 자체가 아직 없다), thisMonth* 는 Settlement.confirmedAt이 이번 달인 건 기준이다.
public record AdminSettlementSummaryResponse(
        long pendingCount,
        long pendingAmount,
        long thisMonthConfirmedCount,
        long thisMonthConfirmedAmount
) {
}
