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
import org.hibernate.type.SqlTypes;

// PG 승인은 성공했으나 그 결과를 DB에 반영하지 못한 건. 실제 돈은 빠져나갔는데 시스템엔 기록이 없는 상태라
// 사람이 PG 콘솔과 대조해 수동 처리해야 한다 (보상 트랜잭션 대신 채택한 방식 - 01-consumer.md 참고)
@Entity
@Table(name = "payment_failure_log")
public class PaymentFailureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "failure_type", nullable = false, columnDefinition = "payment_failure_type")
    private PaymentFailureType failureType;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "group_buy_participant_id", nullable = false)
    private Long groupBuyParticipantId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // Payment 행 자체가 커밋되지 않았을 수 있어 null일 수 있다
    @Column(name = "payment_id")
    private Long paymentId;

    // 수동 대사의 기준값 - PG에는 남아있는 승인 건을 찾는 열쇠
    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Column(nullable = false)
    private int amount;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(nullable = false)
    private boolean resolved;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PaymentFailureLog() {
    }

    private PaymentFailureLog(PaymentFailureType failureType, String eventId, Long groupBuyParticipantId,
            Long memberId, Long paymentId, String pgTransactionId, int amount, String detail) {
        this.failureType = failureType;
        this.eventId = eventId;
        this.groupBuyParticipantId = groupBuyParticipantId;
        this.memberId = memberId;
        this.paymentId = paymentId;
        this.pgTransactionId = pgTransactionId;
        this.amount = amount;
        this.detail = detail;
        this.resolved = false;
    }

    public static PaymentFailureLog of(PaymentFailureType failureType, String eventId, Long groupBuyParticipantId,
            Long memberId, Long paymentId, String pgTransactionId, int amount, String detail) {
        return new PaymentFailureLog(failureType, eventId, groupBuyParticipantId, memberId, paymentId, pgTransactionId,
                amount, detail);
    }

    public void resolve() {
        this.resolved = true;
    }

    public Long getId() {
        return id;
    }

    public PaymentFailureType getFailureType() {
        return failureType;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getGroupBuyParticipantId() {
        return groupBuyParticipantId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getPgTransactionId() {
        return pgTransactionId;
    }

    public int getAmount() {
        return amount;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isResolved() {
        return resolved;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
