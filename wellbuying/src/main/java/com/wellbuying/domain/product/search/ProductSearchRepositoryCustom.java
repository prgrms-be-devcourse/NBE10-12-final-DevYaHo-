package com.wellbuying.domain.product.search;

import com.wellbuying.global.dto.CursorPageResponse;

public interface ProductSearchRepositoryCustom {

    CursorPageResponse<ProductSearchResponse> search(String keyword, SearchSortType sort, String cursor, int size);
}
