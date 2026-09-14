package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.global.dto.CursorPageResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryRepository {

    CursorPageResponse<ProductSummaryResponse> search(ProductSearchCondition condition, String cursor, int size);
    Page<ProductMineResponse> findBySeller(Long sellerId, String keyword, Pageable pageable);
    List<ProductSummaryResponse> findTopByViewCount(int limit);
}
