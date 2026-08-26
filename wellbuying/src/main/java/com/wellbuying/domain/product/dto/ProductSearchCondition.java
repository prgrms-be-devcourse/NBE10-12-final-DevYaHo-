package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.ProductSortType;

public record ProductSearchCondition(
        Long categoryId,
        Integer minPrice,
        Integer maxPrice,
        ProductSortType sort
) {
}