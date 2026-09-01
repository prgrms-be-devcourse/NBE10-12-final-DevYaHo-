package com.wellbuying.domain.notification.dto;

import com.wellbuying.domain.notification.entity.Notification;
import com.wellbuying.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Long groupBuyId,
        Long productId,
        String message,
        boolean read,
        LocalDateTime createdAt
) {

    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getGroupBuyId(),
                notification.getProductId(), notification.getMessage(), notification.isRead(),
                notification.getCreatedAt());
    }
}
