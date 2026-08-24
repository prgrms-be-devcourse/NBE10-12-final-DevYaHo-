package com.wellbuying.groupbuy.event;

import com.wellbuying.groupbuy.domain.GroupBuy;
import java.time.LocalDateTime;

// 공동구매 취소(시작 전) 이벤트 - 참여자가 없는 상태에서만 취소 가능하므로 집계 단위로 1건만 발행
public record GroupBuyCanceledEvent(
        String eventType,
        Long groupBuyId,
        Long productId,
        Long producerId,
        LocalDateTime occurredAt
) {

    public static GroupBuyCanceledEvent of(GroupBuy groupBuy) {
        return new GroupBuyCanceledEvent(
                GroupBuyEventType.GROUP_BUY_CANCELED.code(),
                groupBuy.getId(),
                groupBuy.getProductId(),
                groupBuy.getProducerId(),
                LocalDateTime.now());
    }
}
