package com.wellbuying.domain.groupbuy.domain;

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

@Entity
@Table(name = "group_buy")
public class GroupBuy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "group_buy_status")
    private GroupBuyStatus status;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "min_quantity", nullable = false)
    private int minQuantity;

    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity;

    @Column(name = "current_quantity", nullable = false)
    private int currentQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected GroupBuy() {
    }

    private GroupBuy(Long productId, Long producerId, String title, LocalDateTime startAt, LocalDateTime endAt,
            int minQuantity, int maxQuantity) {
        this.productId = productId;
        this.producerId = producerId;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
        this.status = GroupBuyStatus.READY;
        this.currentQuantity = 0;
    }

    // 공동구매 생성 - status=READY, currentQuantity=0으로 시작
    public static GroupBuy create(Long productId, Long producerId, String title, LocalDateTime startAt,
            LocalDateTime endAt, int minQuantity, int maxQuantity) {
        return new GroupBuy(productId, producerId, title, startAt, endAt, minQuantity, maxQuantity);
    }

    // 시작 시각 도래 - READY -> ONGOING (GroupBuyLifecycleScheduler에서 호출)
    public void start() {
        this.status = GroupBuyStatus.ONGOING;
    }

    // 시작 전 취소 - READY -> CANCELED
    public void cancel() {
        this.status = GroupBuyStatus.CANCELED;
    }

    // 목표 수량 달성 확정 - ONGOING -> SUCCESS
    public void succeed() {
        this.status = GroupBuyStatus.SUCCESS;
    }

    // 마감까지 목표 미달 확정 - ONGOING -> FAILED
    public void fail() {
        this.status = GroupBuyStatus.FAILED;
    }

    // 참여 확정 시 누적 참여 수량 증가 (Redis 원자적 카운터로 재고 초과 여부는 이미 검증된 상태)
    public void increaseQuantity(int quantity) {
        this.currentQuantity += quantity;
    }

    // 참여 취소 시 누적 참여 수량 원복
    public void decreaseQuantity(int quantity) {
        this.currentQuantity = Math.max(0, this.currentQuantity - quantity);
    }

    // READY 상태에서만 허용되는 정보 수정 (null인 필드는 유지)
    public void updateInfo(String title, LocalDateTime endAt) {
        if (title != null) {
            this.title = title;
        }
        if (endAt != null) {
            this.endAt = endAt;
        }
    }

    public boolean isSoldOut() {
        return currentQuantity >= maxQuantity;
    }

    public boolean reachedMinQuantity() {
        return currentQuantity >= minQuantity;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getProducerId() {
        return producerId;
    }

    public String getTitle() {
        return title;
    }

    public GroupBuyStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public int getMinQuantity() {
        return minQuantity;
    }

    public int getMaxQuantity() {
        return maxQuantity;
    }

    public int getCurrentQuantity() {
        return currentQuantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
