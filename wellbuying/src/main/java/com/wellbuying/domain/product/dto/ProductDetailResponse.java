package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.Product;

public record ProductDetailResponse(
        Long id,
        String productName,
        String description,
        Integer startPrice,
        String thumbnailUrl,
        boolean approved
) {

    public static ProductDetailResponse of(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getStartPrice(),
                product.getThumbnailUrl(),
                product.isApproved());
    }
}
