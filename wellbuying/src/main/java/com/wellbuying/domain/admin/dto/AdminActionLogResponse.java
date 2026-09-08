package com.wellbuying.domain.admin.dto;

import com.wellbuying.domain.admin.entity.AdminActionLog;
import com.wellbuying.domain.admin.entity.AdminActionType;
import java.time.LocalDateTime;

public record AdminActionLogResponse(
        Long id,
        Long targetId,
        String targetLabel,
        Long adminId,
        String adminName,
        AdminActionType action,
        String reason,
        LocalDateTime occurredAt
) {

    // targetLabel/adminName이 없으면(이론상 항상 존재) 빈 값으로 안전하게 처리
    public static AdminActionLogResponse of(AdminActionLog log, String targetLabel, String adminName) {
        return new AdminActionLogResponse(
                log.getId(),
                log.getTargetId(),
                targetLabel,
                log.getAdminId(),
                adminName,
                log.getAction(),
                log.getReason(),
                log.getOccurredAt());
    }
}
