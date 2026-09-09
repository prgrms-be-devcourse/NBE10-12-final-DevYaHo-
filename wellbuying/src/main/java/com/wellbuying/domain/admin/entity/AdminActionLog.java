package com.wellbuying.domain.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 관리자가 판매자/상품/공동구매 판매정지 요청을 승인·거절·정지·정지복귀 처리할 때마다 남기는 감사 이력.
// target_id는 target_type에 따라 seller_info/product/group_buy_suspension_request의 id를 가리킨다
@Entity
@Table(name = "admin_action_log")
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "target_type", nullable = false, columnDefinition = "admin_action_target_type")
    private AdminActionTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "admin_action_type")
    private AdminActionType action;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    protected AdminActionLog() {
    }

    private AdminActionLog(AdminActionTargetType targetType, Long targetId, Long adminId, AdminActionType action,
            String reason) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.adminId = adminId;
        this.action = action;
        this.reason = reason;
    }

    public static AdminActionLog record(AdminActionTargetType targetType, Long targetId, Long adminId,
            AdminActionType action, String reason) {
        return new AdminActionLog(targetType, targetId, adminId, action, reason);
    }

    public Long getId() {
        return id;
    }

    public AdminActionTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getAdminId() {
        return adminId;
    }

    public AdminActionType getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
