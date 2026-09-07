package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.search.ProductSearchRepository;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.search.SearchSortType;
import com.wellbuying.global.dto.CursorPageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductSearchService {

    private final ProductSearchRepository productSearchRepository;

    public ProductSearchService(ProductSearchRepository productSearchRepository) {
        this.productSearchRepository = productSearchRepository;
    }

    public CursorPageResponse<ProductSearchResponse> search(String keyword, SearchSortType sort, String cursor, int size) {
        sort.validateSupported();
        return productSearchRepository.search(keyword, cursor, size);
    }
}
