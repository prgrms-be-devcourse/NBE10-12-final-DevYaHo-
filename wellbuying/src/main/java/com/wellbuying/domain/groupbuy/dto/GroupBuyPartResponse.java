package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import java.time.LocalDateTime;

// appliedPrice는 공동구매가 성사되기 전까지 null - 확정 전 예상가는 프론트에서 가격구간(GET /price)과
// 실시간 상태(GET /status)를 조합해 직접 계산해 보여준다
public record GroupBuyPartResponse(
        Long id,
        Long groupBuyId,
        int quantity,
        Integer appliedPrice,
        GroupBuyPartStatus status,
        Long buyerAddressId,
        LocalDateTime createdAt
) {

    public static GroupBuyPartResponse of(GroupBuyPart part) {
        return new GroupBuyPartResponse(
                part.getId(),
                part.getGroupBuyId(),
                part.getQuantity(),
                part.getAppliedPrice(),
                part.getStatus(),
                part.getBuyerAddressId(),
                part.getCreatedAt());
    }
}
