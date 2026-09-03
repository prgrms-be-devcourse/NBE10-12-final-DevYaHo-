package com.wellbuying.domain.product.search;

public record ProductSearchResponse(
        Long id,
        String productName,
        Integer startPrice,
        String thumbnailUrl,
        Long viewCount
) {
    public static ProductSearchResponse from(ProductSearchDocument doc) {
        return new ProductSearchResponse(
                doc.id(),
                doc.productName(),
                doc.startPrice(),
                doc.thumbnailUrl(),
                doc.viewCount()
        );
    }
}
