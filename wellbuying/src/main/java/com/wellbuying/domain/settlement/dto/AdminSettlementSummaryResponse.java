package com.wellbuying.domain.settlement.dto;

// 관리자 정산 대시보드 상단 요약 카드 3개(생산자 대시보드와 같은 구성).
// thisMonthTotalSales/previousMonthTotalSales: settlement_item.paidAt 기준 전체 매출(판매자 구분 없음, 상태 무관)
// pendingCount/pendingAmount: settlement_item(ACCRUED) 기준(정산 확정 전이라 Settlement 행 자체가 아직 없음)
// thisMonth* Confirmed: Settlement.confirmedAt이 이번 달인 건 기준
public record AdminSettlementSummaryResponse(
        long thisMonthTotalSales,
        long previousMonthTotalSales,
        long pendingCount,
        long pendingAmount,
        long thisMonthConfirmedCount,
        long thisMonthConfirmedAmount
) {
}
