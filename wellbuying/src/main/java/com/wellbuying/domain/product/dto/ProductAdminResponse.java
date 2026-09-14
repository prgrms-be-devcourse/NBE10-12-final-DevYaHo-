package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import java.time.LocalDateTime;

// 관리자 상품 심사 목록 조회 응답 DTO
public record ProductAdminResponse(
        Long id,
        Long sellerId,
        String sellerEmail,
        Long categoryId,
        String productName,
        Integer startPrice,
        String thumbnailUrl,
        ProductStatus status,
        LocalDateTime createdAt
) {

    // sellerEmail은 배치 조회한 Map에서 채워 넣는다 - 목록 API에서 상품마다 회원을 개별 조회하지 않기 위함
    public static ProductAdminResponse of(Product product, String sellerEmail) {
        return new ProductAdminResponse(product.getId(), product.getSellerId(), sellerEmail, product.getCategoryId(),
                product.getProductName(), product.getStartPrice(), product.getThumbnailUrl(),
                product.getStatus(), product.getCreatedAt());
    }
}
