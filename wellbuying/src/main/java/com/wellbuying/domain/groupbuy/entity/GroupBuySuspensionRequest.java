package com.wellbuying.domain.groupbuy.entity;

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

// 생산자가 ONGOING 공동구매의 판매정지를 요청하면 관리자가 승인/반려한다. 승인되면 GroupBuy.suspended가 true로 바뀐다
@Entity
@Table(name = "group_buy_suspension_request")
public class GroupBuySuspensionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "group_buy_suspension_status")
    private GroupBuySuspensionStatus status;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    protected GroupBuySuspensionRequest() {
    }

    private GroupBuySuspensionRequest(Long groupBuyId, Long requesterId, String reason) {
        this.groupBuyId = groupBuyId;
        this.requesterId = requesterId;
        this.reason = reason;
        this.status = GroupBuySuspensionStatus.PENDING;
    }

    public static GroupBuySuspensionRequest request(Long groupBuyId, Long requesterId, String reason) {
        return new GroupBuySuspensionRequest(groupBuyId, requesterId, reason);
    }

    public void approve() {
        this.status = GroupBuySuspensionStatus.APPROVED;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = GroupBuySuspensionStatus.REJECTED;
        this.decidedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public String getReason() {
        return reason;
    }

    public GroupBuySuspensionStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
}
