package com.wellbuying.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "product_count")
public class ProductCount {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "groupbuy_participant_count", nullable = false)
    private Long groupbuyParticipantCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProductCount() {
    }

    private ProductCount(Long productId) {
        this.productId = productId;
        this.viewCount = 0L;
        this.likeCount = 0L;
        this.groupbuyParticipantCount = 0L;
    }

    // 상품 등록 시 함께 호출 - 조회수/찜수/공동구매 참여자 수를 모두 0으로 초기화한 통계 레코드 생성
    public static ProductCount init(Long productId) {
        return new ProductCount(productId);
    }

    // 조회수를 1 증가
    public void increaseViewCount() {
        this.viewCount++;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public Long getGroupbuyParticipantCount() {
        return groupbuyParticipantCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}