package com.wellbuying.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_price", nullable = false)
    private Integer startPrice;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "status", nullable = false)
    private boolean available;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Product() {
    }

    private Product(Long sellerId, Long categoryId, String productName, String description,
                    Integer startPrice, String thumbnailUrl) {
        if (sellerId == null || categoryId == null) {
            throw new IllegalArgumentException("판매자와 카테고리는 필수입니다");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (startPrice == null || startPrice < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다");
        }
        this.sellerId = sellerId;
        this.categoryId = categoryId;
        this.productName = productName;
        this.description = description;
        this.startPrice = startPrice;
        this.thumbnailUrl = thumbnailUrl;
        this.available = true;
    }

    // 판매자가 입력한 정보로 상품을 생성, 기본적으로 판매 가능(available=true) 상태로 시작
    public static Product register(Long sellerId, Long categoryId, String productName,
                                   String description, Integer startPrice, String thumbnailUrl) {
        return new Product(sellerId, categoryId, productName, description, startPrice, thumbnailUrl);
    }

    // 판매 가능 상태를 false로 변경해 품절 처리
    public void markSoldOut() {
        this.available = false;
    }

    // 품절 상태를 다시 판매 가능으로 변경
    public void markAvailable() {
        this.available = true;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getProductName() {
        return productName;
    }

    public String getDescription() {
        return description;
    }

    public Integer getStartPrice() {
        return startPrice;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public boolean isAvailable() {
        return available;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}