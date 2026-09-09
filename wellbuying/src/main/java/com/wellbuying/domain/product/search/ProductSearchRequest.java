package com.wellbuying.domain.product.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProductSearchRequest(
        @NotBlank(message = "검색 키워드는 필수입니다.")
        String keyword,

        SearchSortType sort,

        String cursor,

        @Min(1) @Max(100)
        Integer size,

        Long categoryId,
        @Min(0) Integer minPrice,
        @Min(0) Integer maxPrice,

        Boolean activeGroupBuyOnly
) {
    public ProductSearchRequest {
        if (sort == null) {
            sort = SearchSortType.RELEVANCE;
        }
        if (keyword != null) {
            keyword = keyword.trim();
        }
        if (size == null) {
            size = 20;
        }
        if (activeGroupBuyOnly == null) {
            activeGroupBuyOnly = false;
        }
    }

    @JsonIgnore
    @AssertTrue(message = "최소 가격은 최대 가격보다 클 수 없습니다.")
    public boolean isValidPriceRange() {
        if (minPrice != null && maxPrice != null) {
            return minPrice <= maxPrice;
        }
        return true;
    }

    public ProductSearchFilter toFilter() {
        return new ProductSearchFilter(categoryId, minPrice, maxPrice, activeGroupBuyOnly);
    }
}
