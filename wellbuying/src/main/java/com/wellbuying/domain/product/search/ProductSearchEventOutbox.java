package com.wellbuying.domain.product.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

// 아웃박스 1행 = 발행 대기 중이거나 이미 발행된 검색 인덱스 동기화 이벤트 1건.
// 상품 상태 변경과 같은 트랜잭션 안에서 이 행을 저장하고,
// ProductSearchOutboxRelay/Dispatcher가 별도 트랜잭션에서 폴링해 ES에 반영한 뒤 publishedAt을 채운다.
@Entity
@Table(name = "product_search_event_outbox")
public class ProductSearchEventOutbox {

    // 이 횟수를 넘겨 실패한 이벤트는 poison pill로 간주해 릴레이 폴링 대상에서 제외한다(무한 재시도 방지)
    public static final int MAX_RETRY_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    protected ProductSearchEventOutbox() {
    }

    private ProductSearchEventOutbox(Long productId, String eventType) {
        this.productId = productId;
        this.eventType = eventType;
    }

    // CREATE·UPDATE 모두 동일한 ES upsert 동작이므로 UPSERT 이벤트 하나로 통합
    public static ProductSearchEventOutbox upsert(Long productId) {
        return new ProductSearchEventOutbox(productId, "UPSERT");
    }

    public static ProductSearchEventOutbox delete(Long productId) {
        return new ProductSearchEventOutbox(productId, "DELETE");
    }

    // ES 반영 실패 시 호출 - retryCount가 MAX_RETRY_COUNT에 도달하면 릴레이가 더 이상 이 행을 조회하지 않는다.
    // 실제 DB 반영은 ProductSearchOutboxDispatcher가 배치 전체를 한 번의 UPDATE로 묶어서 하므로, 이 메서드는
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

    public Long getProductId() {
        return productId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }
}
