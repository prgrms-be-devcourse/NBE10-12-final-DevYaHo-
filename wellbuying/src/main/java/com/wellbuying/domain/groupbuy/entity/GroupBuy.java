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

    @Column(nullable = false)
    private boolean suspended;

    // 홈 화면 "인기" 섹션 정렬용 - 상세 조회(GET /{id})마다 GroupBuyRepository.increaseViewCount()로
    // 원자적 벌크 UPDATE되며, 참여 수량과 달리 값이 즉시 이 엔티티에 반영될 필요가 없어 dirty checking을 쓰지 않는다
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    // 성사(SUCCESS) 확정 시점에는 채워지지 않는다 - GroupBuyFinalizationWorker가 참여자 최종가 반영 +
    // outbox 이벤트 기록을 마친 뒤에만 채운다. null이면 "성사는 됐지만 아직 확정 참여자 후속 처리 전"이라는 뜻
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

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
        this.suspended = false;
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

    // 관리자가 판매정지 요청을 승인 - ONGOING이던 공동구매를 강제 취소한다. suspended 플래그는 CANCELED가 된
    // 사유(판매정지 vs. 생산자의 시작 전 자진 취소)를 구분하기 위해 status와 별도로 유지한다
    public void suspend() {
        this.suspended = true;
        this.status = GroupBuyStatus.CANCELED;
    }

    // GroupBuyFinalizationWorker가 확정 참여자 최종가 반영 + outbox 이벤트 기록까지 마쳤음을 표시
    public void markFinalized() {
        this.finalizedAt = LocalDateTime.now();
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

    public boolean isSuspended() {
        return suspended;
    }

    public long getViewCount() {
        return viewCount;
    }

    public LocalDateTime getFinalizedAt() {
        return finalizedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
