package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.search.ProductSearchRepository;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.search.SearchSortType;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {

    private final ProductSearchRepository productSearchRepository;

    public ProductSearchService(ProductSearchRepository productSearchRepository) {
        this.productSearchRepository = productSearchRepository;
    }

    public Slice<ProductSearchResponse> search(String keyword, SearchSortType sort, int page, int size) {
        return productSearchRepository.search(keyword, sort, page, size);
    }
}
