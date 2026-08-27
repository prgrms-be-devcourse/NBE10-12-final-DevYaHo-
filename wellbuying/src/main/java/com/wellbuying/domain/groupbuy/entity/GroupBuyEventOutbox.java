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

    // 이 횟수를 넘겨 실패한 이벤트는 poison pill로 간주해 릴레이 폴링 대상에서 제외한다(무한 재시도 방지)
    public static final int MAX_RETRY_COUNT = 5;

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

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

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

    // Kafka 발행 실패 시 호출 - retryCount가 MAX_RETRY_COUNT에 도달하면 릴레이가 더 이상 이 행을 조회하지 않는다.
    // 실제 DB 반영은 GroupBuyOutboxDispatcher가 배치 전체를 한 번의 UPDATE로 묶어서 하므로, 이 메서드는
    // 그 UPDATE 이전에 "이 실패로 재시도가 소진되는지"를 판단하기 위해 메모리 상에서만 호출된다
    public void recordFailure() {
        this.retryCount++;
    }

    public boolean isRetryExhausted() {
        return retryCount >= MAX_RETRY_COUNT;
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

    public int getRetryCount() {
        return retryCount;
    }
}
