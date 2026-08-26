package com.wellbuying.domain.groupbuy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

// 아웃박스 1행 = 발행 대기 중이거나 이미 발행된 공동구매 이벤트 1건.
// GroupBuyEventPublisher가 상태 변경과 같은 트랜잭션 안에서 이 행을 저장하고,
// GroupBuyOutboxRelay/Dispatcher가 별도 트랜잭션에서 폴링해 Kafka로 발행한 뒤 publishedAt을 채운다.
@Entity
@Table(name = "group_buy_event_outbox")
public class GroupBuyEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected GroupBuyEventOutbox() {
    }

    private GroupBuyEventOutbox(Long groupBuyId, String eventType, String payload) {
        this.groupBuyId = groupBuyId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public static GroupBuyEventOutbox of(Long groupBuyId, String eventType, String payload) {
        return new GroupBuyEventOutbox(groupBuyId, eventType, payload);
    }

    // Kafka 발행 성공 시 호출 - 이후 릴레이의 미발행 조회 대상에서 빠진다
    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}
