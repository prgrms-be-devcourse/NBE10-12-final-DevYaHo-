package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import java.time.Duration;
import java.time.LocalDateTime;

// 실시간 상태 조회 - 참여자 수, 잔여 수량, 남은 시간처럼 자주 바뀌는 값만 담는다
public record GroupBuyStatusResponse(
        Long id,
        GroupBuyStatus status,
        int currentQuantity,
        int remainingQuantity,
        long participantCount,
        long remainingSeconds
) {

    public static GroupBuyStatusResponse of(GroupBuy groupBuy, long participantCount) {
        long remainingSeconds = Math.max(0, Duration.between(LocalDateTime.now(), groupBuy.getEndAt()).getSeconds());
        int remainingQuantity = Math.max(0, groupBuy.getMaxQuantity() - groupBuy.getCurrentQuantity());
        return new GroupBuyStatusResponse(
                groupBuy.getId(),
                groupBuy.getStatus(),
                groupBuy.getCurrentQuantity(),
                remainingQuantity,
                participantCount,
                remainingSeconds);
    }
}
