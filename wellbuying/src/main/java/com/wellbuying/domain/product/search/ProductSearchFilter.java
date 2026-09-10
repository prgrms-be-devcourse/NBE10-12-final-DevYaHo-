package com.wellbuying.domain.product.search;

// 검색 필터 묶음 - 파라미터가 늘어나도 시그니처가 안 바뀌게 record로 묶는다. 모든 필드 null 허용(= 필터 없음)
public record ProductSearchFilter(Long categoryId, Integer minPrice, Integer maxPrice, Boolean activeGroupBuyOnly) {

    public static ProductSearchFilter none() {
        return new ProductSearchFilter(null, null, null, false);
    }

    public boolean hasPriceRange() {
        return minPrice != null || maxPrice != null;
    }
}