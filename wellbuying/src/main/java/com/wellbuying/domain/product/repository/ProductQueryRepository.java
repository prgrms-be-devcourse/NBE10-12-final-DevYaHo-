package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface ProductQueryRepository {

    // 카테고리/가격 필터와 정렬 조건에 맞는 상품 목록을 페이지 단위로 조회 (구현은 ProductQueryRepositoryImpl)
    Slice<ProductSummaryResponse> search(ProductSearchCondition condition, Pageable pageable);
    Slice<ProductMineResponse> findBySeller(Long sellerId, Pageable pageable);
}