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

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProductCategory() {
    }

    public static ProductCategory create(Long parentId, String categoryName, Integer sortOrder) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("카테고리명은 필수입니다");
        }
        ProductCategory category = new ProductCategory();
        category.parentId = parentId;
        category.categoryName = categoryName;
        category.sortOrder = sortOrder != null ? sortOrder : 0;
        return category;
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    // 카테고리명, 정렬 순서 변경
    public void update(String categoryName, Integer sortOrder) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException("카테고리명은 필수입니다");
        }
        this.categoryName = categoryName;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}