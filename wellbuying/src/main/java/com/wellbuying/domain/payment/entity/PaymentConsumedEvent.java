package com.wellbuying.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

// Kafka 메시지 중복 수신 방어용 처리 이력 (수신 측 전용 - 발행 측 유실 보장은 Outbox 소관이며 별개다)
@Entity
@Table(name = "payment_consumed_event")
public class PaymentConsumedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 발행 측 이벤트에 고유 id 필드가 아직 없어 {eventType}:{partId}로 조합한 값을 쓴다
    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    protected PaymentConsumedEvent() {
    }

    private PaymentConsumedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    public static PaymentConsumedEvent of(String eventId, String eventType) {
        return new PaymentConsumedEvent(eventId, eventType);
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
