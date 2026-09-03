package com.wellbuying.domain.product.dto;

public record ProductSummaryResponse(
        Long id,
        String productName,
        Integer startPrice,
        String thumbnailUrl,
        Long viewCount
) {
}