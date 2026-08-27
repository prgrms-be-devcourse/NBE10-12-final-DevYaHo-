package com.wellbuying.domain.groupbuy.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionRequest;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionStatus;
import java.time.LocalDateTime;

public record GroupBuySuspensionRequestResponse(
        Long id,
        Long groupBuyId,
        String groupBuyTitle,
        Long requesterId,
        String reason,
        GroupBuySuspensionStatus status,
        LocalDateTime requestedAt,
        LocalDateTime decidedAt
) {

    // groupBuyTitle이 없으면(이론상 항상 존재) 빈 값으로 안전하게 처리
    public static GroupBuySuspensionRequestResponse of(GroupBuySuspensionRequest request, String groupBuyTitle) {
        return new GroupBuySuspensionRequestResponse(
                request.getId(),
                request.getGroupBuyId(),
                groupBuyTitle,
                request.getRequesterId(),
                request.getReason(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getDecidedAt());
    }
}
