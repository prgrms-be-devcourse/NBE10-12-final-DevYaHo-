package com.wellbuying.domain.product.search;

public record ProductSearchResponse(
        Long id,
        String productName,
        Integer startPrice,
        String thumbnailUrl,
        Long viewCount,
        Boolean hasActiveGroupBuy,
        String groupBuyStatus,
        Integer currentUnitPrice,
        Integer participantCount,
        Integer targetQuantity
) {
    public static ProductSearchResponse from(ProductSearchDocument doc) {
        return new ProductSearchResponse(
                doc.id(),
                doc.productName(),
                doc.startPrice(),
                doc.thumbnailUrl(),
                doc.viewCount(),
                doc.hasActiveGroupBuy(),
                doc.groupBuyStatus(),
                doc.currentUnitPrice(),
                doc.participantCount(),
                doc.targetQuantity()
        );
    }
}
