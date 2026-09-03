package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.ProductStatus;
import java.time.LocalDateTime;

public record ProductMineResponse(
        Long id,
        String productName,
        Integer startPrice,
        String thumbnailUrl,
        ProductStatus status,
        LocalDateTime createdAt
) {
}