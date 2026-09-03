package com.wellbuying.domain.product.search;

import com.wellbuying.global.dto.CursorPageResponse;

public interface ProductSearchRepositoryCustom {

    CursorPageResponse<ProductSearchResponse> search(String keyword, String cursor, int size);
}
