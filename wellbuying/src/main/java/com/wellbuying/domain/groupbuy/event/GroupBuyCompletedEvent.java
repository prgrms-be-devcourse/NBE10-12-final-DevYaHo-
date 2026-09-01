package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import java.time.LocalDateTime;

// 공동구매 성사 이벤트 - 참여자 1명당 1건씩 발행되어, 결제 도메인이 참여자 단위로 결제를 개시할 수 있게 한다
public record GroupBuyCompletedEvent(
        String eventType,
        Long groupBuyId,
        Long productId,
        Long producerId,
        Long partId,
        Long memberId,
        int quantity,
        int appliedPrice,
        LocalDateTime occurredAt
) {

    public static GroupBuyCompletedEvent of(GroupBuy groupBuy, GroupBuyPart part) {
        return new GroupBuyCompletedEvent(
                GroupBuyEventType.GROUP_BUY_COMPLETED.code(),
                groupBuy.getId(),
                groupBuy.getProductId(),
                groupBuy.getProducerId(),
                part.getId(),
                part.getMemberId(),
                part.getQuantity(),
                part.getAppliedPrice(),
                LocalDateTime.now());
    }
}
