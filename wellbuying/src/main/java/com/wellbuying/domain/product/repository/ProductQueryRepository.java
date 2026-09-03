package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.global.dto.CursorPageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductQueryRepository {

    CursorPageResponse<ProductSummaryResponse> search(ProductSearchCondition condition, String cursor, int size);
    Slice<ProductMineResponse> findBySeller(Long sellerId, Pageable pageable);
}
