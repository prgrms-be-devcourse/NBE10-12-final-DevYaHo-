package com.wellbuying.domain.settlement.dto;

import java.time.LocalDateTime;

// 매출 추이 그래프의 점 하나 (주 또는 월 단위 구간).
// settlement_item.paidAt(실제 결제 시점) 기준으로 집계한다 - 정산 확정 여부와 무관하게
// "언제 팔렸는지"를 보여준다 (정산 처리 지연과 분리 - SettlementStatsService 클래스 주석 참고).
public record SettlementTrendPointResponse(
        LocalDateTime periodStart,
        long totalSales,
        int itemCount
) {
}
