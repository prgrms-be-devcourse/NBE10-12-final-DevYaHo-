package com.wellbuying.domain.product.search;

import java.time.LocalDateTime;

public record ProductSearchResponse(
        Long id,
        String productName,
        Integer startPrice,
        String thumbnailUrl,
        Long viewCount,
        Boolean hasActiveGroupBuy,
        Long groupBuyId,
        String groupBuyStatus,
        Integer currentUnitPrice,
        Integer currentQuantity,
        Integer targetQuantity,
        Integer maxQuantity,
        LocalDateTime endAt
) {
    public static ProductSearchResponse from(ProductSearchDocument doc) {
        return new ProductSearchResponse(
                doc.id(),
                doc.productName(),
                doc.startPrice(),
                doc.thumbnailUrl(),
                doc.viewCount(),
                doc.hasActiveGroupBuy(),
                doc.groupBuyId(),
                doc.groupBuyStatus(),
                doc.currentUnitPrice(),
                doc.currentQuantity(),
                doc.targetQuantity(),
                doc.maxQuantity(),
                doc.endAt()
        );
    }
}
