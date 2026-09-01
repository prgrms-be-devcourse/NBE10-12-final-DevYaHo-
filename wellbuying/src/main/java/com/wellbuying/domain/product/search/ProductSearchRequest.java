package com.wellbuying.domain.product.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ProductSearchRequest(
        @NotBlank(message = "검색 키워드는 필수입니다.")
        String keyword,

        SearchSortType sort,

        @Min(0)
        Integer page,

        @Min(1) @Max(100)
        Integer size
) {
    public ProductSearchRequest {
        if (sort == null) {
            sort = SearchSortType.RELEVANCE;
        }
        if (keyword != null) {
            keyword = keyword.trim();
        }
        if (page == null) {
            page = 0;
        }
        if (size == null) {
            size = 20;
        }
    }
}
