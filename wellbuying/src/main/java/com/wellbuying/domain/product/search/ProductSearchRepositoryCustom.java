package com.wellbuying.domain.product.search;

import com.wellbuying.global.dto.CursorPageResponse;
import java.util.List;

public interface ProductSearchRepositoryCustom {

    CursorPageResponse<ProductSearchResponse> search(String keyword, String cursor, int size, ProductSearchFilter filter);

    List<ProductAutocompleteResponse> autocomplete(String keyword);
}
