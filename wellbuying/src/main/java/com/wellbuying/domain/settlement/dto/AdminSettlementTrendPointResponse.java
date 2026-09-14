package com.wellbuying.domain.settlement.dto;

import java.time.LocalDateTime;

// 관리자 매출 추이 그래프의 점 하나 (판매자 구분 없는 플랫폼 전체 집계).
// settlement_item.paidAt(실제 결제 시점) 기준 - SettlementTrendPointResponse와 같은 이유로 확정
// 여부와 분리한다. platformFee는 그 구간 totalSales에 수수료율을 곱해 원 단위로 버린 값(SettlementConfirmationService와
// 같은 계산식) - 실제 확정된 settlement.platformFee 합계가 아니라 그래프용 추정치다(확정 전 구간이 섞여 있어서).
public record AdminSettlementTrendPointResponse(
        LocalDateTime periodStart,
        long totalSales,
        long platformFee,
        int groupBuyCount
) {
}
