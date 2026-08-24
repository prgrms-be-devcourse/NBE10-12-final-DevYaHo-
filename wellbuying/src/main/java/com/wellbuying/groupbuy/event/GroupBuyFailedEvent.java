package com.wellbuying.groupbuy.event;

import com.wellbuying.groupbuy.domain.GroupBuy;
import java.time.LocalDateTime;

// 공동구매 실패(마감까지 최소 수량 미달) 이벤트 - 참여자별 결제가 없는 단계이므로 집계 단위로 1건만 발행
public record GroupBuyFailedEvent(
        String eventType,
        Long groupBuyId,
        Long productId,
        Long producerId,
        int currentQuantity,
        int minQuantity,
        LocalDateTime occurredAt
) {

    public static GroupBuyFailedEvent of(GroupBuy groupBuy) {
        return new GroupBuyFailedEvent(
                GroupBuyEventType.GROUP_BUY_FAILED.code(),
                groupBuy.getId(),
                groupBuy.getProductId(),
                groupBuy.getProducerId(),
                groupBuy.getCurrentQuantity(),
                groupBuy.getMinQuantity(),
                LocalDateTime.now());
    }
}
