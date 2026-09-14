package com.wellbuying.domain.settlement.dto;

// 정산 대시보드 상단 요약 카드 3개.
// - thisMonthTotalSales: 이번 달 1일~오늘, paidAt 기준 전체 매출 (상태 무관 - 판매는 판매)
// - pendingAmount: "정산 대기 중" 카드 = ACCRUED 전체 금액. 월과 무관한 현재 스냅샷이다 -
//   유예기간(기본 3일) 안에 걸려 있어 아직 배치가 확정하지 않은 돈이 얼마인지를 보여준다
// - thisMonthSettledAmount: 이번 달 매출 중 이미 정산 확정(CONFIRMED)까지 끝난 몫
//   (이번 달 매출과 짝을 맞추려고 paidAt·상태 둘 다 이번 달 + CONFIRMED로 건다)
public record SettlementMonthlySummaryResponse(
        String yearMonth,
        long thisMonthTotalSales,
        int thisMonthItemCount,
        long previousMonthTotalSales,
        long pendingAmount,
        int pendingItemCount,
        long thisMonthSettledAmount,
        int thisMonthSettledItemCount
) {
}
