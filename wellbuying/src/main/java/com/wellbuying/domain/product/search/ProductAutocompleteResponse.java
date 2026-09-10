package com.wellbuying.domain.product.search;

// 자동완성 응답 - 상세 정보 없이 상품명만 가볍게 반환
public record ProductAutocompleteResponse(Long id, String productName) {
    public static ProductAutocompleteResponse from(ProductSearchDocument doc) {
        return new ProductAutocompleteResponse(doc.id(), doc.productName());
    }
}