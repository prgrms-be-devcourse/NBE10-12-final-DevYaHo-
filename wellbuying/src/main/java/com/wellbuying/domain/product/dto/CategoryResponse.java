package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.ProductCategory;

public record CategoryResponse(
        Long id,
        Long parentId,
        String categoryName,
        Integer sortOrder
) {
    public static CategoryResponse from(ProductCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getCategoryName(),
                category.getSortOrder()
        );
    }
}
