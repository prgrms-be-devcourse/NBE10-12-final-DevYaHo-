package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.search.ProductSearchRepository;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.search.SearchSortType;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Service
public class ProductSearchService {

    private final ProductSearchRepository productSearchRepository;

    public ProductSearchService(ProductSearchRepository productSearchRepository) {
        this.productSearchRepository = productSearchRepository;
    }

    public Slice<ProductSearchResponse> search(String keyword, SearchSortType sort, int page, int size) {
        // TODO: 최신순, 가격순 정렬 확장 시 이 분기 추가
        if (sort != SearchSortType.RELEVANCE) {
            throw new BusinessException(ErrorCode.SEARCH_SORT_NOT_SUPPORTED);
        }
        return productSearchRepository.search(keyword, sort, page, size);
    }
}
