package com.wellbuying.domain.settlement.dto;

// PENDING 정산 건 상세 - "몇 명 중 몇 명이 결제했는지" 진행도.
// totalParticipants: 확정 참여자(GroupBuyPartStatus.CONFIRMED) 전체 수
// paidParticipants: 그중 결제까지 완료해 settlement_item이 적립된 수 (ACCRUED/CONFIRMED 무관 - 존재 자체가 결제 완료를 뜻함)
public record SettlementProgressResponse(
        long totalParticipants,
        long paidParticipants
) {
}
