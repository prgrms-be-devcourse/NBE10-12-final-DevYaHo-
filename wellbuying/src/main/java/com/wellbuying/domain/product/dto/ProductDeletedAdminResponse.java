package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.Product;
import java.time.LocalDateTime;

public record ProductDeletedAdminResponse(
        Long id, Long sellerId, String productName,
        LocalDateTime deletedAt, Long deletedBy, String deleteReason) {

    public static ProductDeletedAdminResponse of(Product product) {
        return new ProductDeletedAdminResponse(product.getId(), product.getSellerId(), product.getProductName(),
                product.getDeletedAt(), product.getDeletedBy(), product.getDeleteReason());
    }
}
