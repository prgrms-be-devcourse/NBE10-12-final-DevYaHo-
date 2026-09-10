package com.wellbuying.domain.settlement.dto;

import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import java.time.LocalDateTime;

// 정산 확정 1건 = 공동구매 1건. 표시에 필요한 공동구매 제목/판매자 이름은 조회 시점에 각 도메인 행을
// 배치로 읽어 조합한다 (settlement에 스냅샷으로 복사하지 않는다 - OrderSummaryResponse와 같은 방식).
public record SettlementResponse(
        Long settlementId,
        Long groupBuyId,
        String groupBuyTitle,
        Long producerId,
        String producerName,
        int itemCount,
        long totalSales,
        long platformFee,
        long payout,
        SettlementStatus status,
        LocalDateTime confirmedAt
) {

    public static SettlementResponse of(Settlement settlement, String groupBuyTitle, String producerName) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroupBuyId(),
                groupBuyTitle,
                settlement.getProducerId(),
                producerName,
                settlement.getItemCount(),
                settlement.getTotalSales(),
                settlement.getPlatformFee(),
                settlement.getPayout(),
                settlement.getStatus(),
                settlement.getConfirmedAt());
    }
}
