package com.wellbuying.domain.payment.entity;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

// 공동구매 참여 1건에 대한 결제. 성사 이벤트를 받으면 먼저 READY로 만들어두고, PG 승인 결과에 따라
// APPROVED / FAILED로 전이한다 (Order는 같은 트랜잭션에서 PENDING으로 함께 만들어져 결과에 따라 전이한다)
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_participant_id", nullable = false)
    private Long groupBuyParticipantId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int amount;

    @Column(name = "pg_provider", nullable = false)
    private String pgProvider;

    // PG 승인 전에는 null - 승인 응답을 받아야 발급된다
    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    // PG에 승인을 재요청해도 중복 결제되지 않게 하는 키. 이벤트 재수신 시에도 같은 값이 나와야 하므로
    // 랜덤이 아니라 이벤트 식별자({eventType}:{partId})를 그대로 쓴다
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "payment_status")
    private PaymentStatus status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Payment() {
    }

    private Payment(Long groupBuyParticipantId, Long memberId, int amount, String pgProvider, String idempotencyKey) {
        this.groupBuyParticipantId = groupBuyParticipantId;
        this.memberId = memberId;
        this.amount = amount;
        this.pgProvider = pgProvider;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.READY;
    }

    // PG 승인을 시도하기 전에 먼저 남겨두는 결제 건 - 승인 도중 장애가 나도 "승인을 시도했다"는 사실이 DB에 남는다
    public static Payment ready(Long groupBuyParticipantId, Long memberId, int amount, String pgProvider,
            String idempotencyKey) {
        return new Payment(groupBuyParticipantId, memberId, amount, pgProvider, idempotencyKey);
    }

    public void approve(String pgTransactionId, LocalDateTime approvedAt) {
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = approvedAt;
        this.status = PaymentStatus.APPROVED;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void cancel(LocalDateTime canceledAt) {
        this.canceledAt = canceledAt;
        this.status = PaymentStatus.CANCELED;
    }

    public boolean isApproved() {
        return status == PaymentStatus.APPROVED;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyParticipantId() {
        return groupBuyParticipantId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getAmount() {
        return amount;
    }

    public String getPgProvider() {
        return pgProvider;
    }

    public String getPgTransactionId() {
        return pgTransactionId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
