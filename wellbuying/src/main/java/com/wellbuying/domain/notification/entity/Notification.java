package com.wellbuying.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

// 알림 1행 = 회원 1명에게 발송된 공동구매 성사/실패 알림 1건.
// GroupBuyEventConsumer가 groupbuy-events 토픽을 구독해 생성한다.
// 인덱스/유니크 제약의 실제 소스는 V21__create_notification.sql이고(ddl-auto=validate라 여기 선언이
// DDL을 생성하지는 않는다), 코드만 보고도 제약을 알 수 있도록 엔티티에도 동일하게 명시해둔다
@Entity
@Table(name = "notification",
        indexes = @Index(name = "idx_notification_member_id_created_at", columnList = "member_id, created_at DESC"),
        uniqueConstraints = @UniqueConstraint(name = "uq_notification_member_group_buy_type",
                columnNames = {"member_id", "group_buy_id", "type"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    private Notification(Long memberId, NotificationType type, Long groupBuyId, Long productId, String message) {
        this.memberId = memberId;
        this.type = type;
        this.groupBuyId = groupBuyId;
        this.productId = productId;
        this.message = message;
    }

    public static Notification of(Long memberId, NotificationType type, Long groupBuyId, Long productId,
            String message) {
        return new Notification(memberId, type, groupBuyId, productId, message);
    }

    public void markAsRead() {
        this.read = true;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public NotificationType getType() {
        return type;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
