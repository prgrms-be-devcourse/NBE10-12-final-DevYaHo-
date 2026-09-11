package com.wellbuying.domain.settlement.dto;

import java.time.LocalDateTime;

// 월별 정산 내역 리스트의 행 하나. PENDING(settlement_item ACCRUED 집계)과 COMPLETED(settlement 확정행)
// 두 출처를 하나의 모양으로 통일한다.
// - finalizedAt이 속한 달로 이 행이 귀속된다 - 나중에 확정돼도(PENDING -> COMPLETED) 달이 안 바뀐다
// - PENDING 행은 settlementId/confirmedAt이 없다(null) - 아직 확정 전이라 존재하지 않는 값
// - platformFee/payout은 PENDING이어도 항상 계산해서 채운다 (SettlementConfirmationService와 같은 식,
//   floor(총매출 x 수수료율)) - 카드 표면에 예상 지급액을 보여주기 위함. 확정 전 값이라는 건 status로 구분한다
public record SettlementListItemResponse(
        Long settlementId,
        Long groupBuyId,
        String groupBuyTitle,
        Long producerId,
        String producerName,
        int itemCount,
        long totalSales,
        long platformFee,
        long payout,
        SettlementListStatus status,
        LocalDateTime finalizedAt,
        LocalDateTime confirmedAt
) {
}
