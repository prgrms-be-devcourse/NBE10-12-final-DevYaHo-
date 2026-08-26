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
@Table(name = "product_category")
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProductCategory() {
    }

    private ProductCategory(Long parentId, String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("카테고리명은 필수입니다");
        }
        this.parentId = parentId;
        this.categoryName = categoryName;
    }

    // 카테고리를 생성, parentId가 null이면 최상위 카테고리로 등록
    public static ProductCategory create(Long parentId, String categoryName) {
        return new ProductCategory(parentId, categoryName);
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}