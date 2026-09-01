package com.wellbuying.domain.product.search;

import org.springframework.data.domain.Slice;

public interface ProductSearchRepositoryCustom {

    Slice<ProductSearchResponse> search(String keyword, SearchSortType sort, int page, int size);
}
