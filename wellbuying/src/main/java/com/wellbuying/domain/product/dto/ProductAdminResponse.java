package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import java.time.LocalDateTime;

// 관리자 상품 심사 목록 조회 응답 DTO
public record ProductAdminResponse(
        Long id,
        Long sellerId,
        Long categoryId,
        String productName,
        Integer startPrice,
        String thumbnailUrl,
        ProductStatus status,
        LocalDateTime createdAt
) {

    public static ProductAdminResponse of(Product product) {
        return new ProductAdminResponse(product.getId(), product.getSellerId(), product.getCategoryId(),
                product.getProductName(), product.getStartPrice(), product.getThumbnailUrl(),
                product.getStatus(), product.getCreatedAt());
    }
}
