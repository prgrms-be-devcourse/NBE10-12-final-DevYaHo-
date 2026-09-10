package com.wellbuying.domain.settlement.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 정산 대상 1행 = 결제 완료(payment-events / PaymentCompleted) 1건.
// SettlementPaymentEventConsumer가 payment-events 토픽을 구독해 참여 건 단위로 적립한다(Phase 1).
// 유예기간이 끝나면 배치가 group_buy 단위로 모아 확정(CONFIRMED)한다(Phase 2).
//
// 인덱스/유니크 제약의 실제 소스는 V41__create_settlement_item.sql이고(ddl-auto=validate라 여기 선언이
// DDL을 생성하지는 않는다), 코드만 보고도 제약을 알 수 있도록 엔티티에도 동일하게 명시해둔다
@Entity
@Table(name = "settlement_item",
        indexes = @Index(name = "idx_settlement_item_accrued", columnList = "group_buy_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_item_group_buy_participant_id",
                columnNames = "group_buy_participant_id"))
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id", nullable = false, updatable = false)
    private Long groupBuyId;

    @Column(name = "group_buy_participant_id", nullable = false, updatable = false)
    private Long groupBuyParticipantId;

    @Column(name = "producer_id", nullable = false, updatable = false)
    private Long producerId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    // 결제 원금. 수수료를 뺀 지급액은 Phase 2에서 계산한다
    @Column(nullable = false, updatable = false)
    private int amount;

    // 결제 승인 시각 (PaymentCompletedEvent.occurredAt)
    @Column(name = "paid_at", nullable = false, updatable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "settlement_item_status")
    private SettlementItemStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SettlementItem() {
    }

    private SettlementItem(Long groupBuyId, Long groupBuyParticipantId, Long producerId, Long memberId, int amount,
            LocalDateTime paidAt) {
        this.groupBuyId = groupBuyId;
        this.groupBuyParticipantId = groupBuyParticipantId;
        this.producerId = producerId;
        this.memberId = memberId;
        this.amount = amount;
        this.paidAt = paidAt;
        this.status = SettlementItemStatus.ACCRUED;
    }

    // 결제 완료 이벤트를 받아 정산 대상으로 적립한다 (Phase 1)
    public static SettlementItem accrue(Long groupBuyId, Long groupBuyParticipantId, Long producerId, Long memberId,
            int amount, LocalDateTime paidAt) {
        return new SettlementItem(groupBuyId, groupBuyParticipantId, producerId, memberId, amount, paidAt);
    }

    // 유예기간이 끝나 배치가 정산을 확정할 때 호출한다 (Phase 2)
    public void confirm() {
        this.status = SettlementItemStatus.CONFIRMED;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getGroupBuyParticipantId() {
        return groupBuyParticipantId;
    }

    public Long getProducerId() {
        return producerId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getAmount() {
        return amount;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public SettlementItemStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
