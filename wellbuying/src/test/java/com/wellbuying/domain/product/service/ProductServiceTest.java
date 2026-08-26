package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

// DB 없이 리포지토리를 가짜(Mock)로 대체해서, ProductService가 조건을 그대로 리포지토리에 위임하는지만 검증
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    // getProducts 호출 시 전달받은 condition/pageable을 그대로 리포지토리에 넘기고, 결과를 그대로 반환한다
    @Test
    void getProducts_리포지토리_결과를_그대로_반환한다() {
        ProductService productService = new ProductService(productRepository);
        ProductSearchCondition condition = new ProductSearchCondition(1L, 1000, 5000, ProductSortType.LATEST);
        PageRequest pageable = PageRequest.of(0, 20);
        ProductSummaryResponse response = new ProductSummaryResponse(1L, "상품", 3000, "url", 0L);
        Slice<ProductSummaryResponse> mockSlice = new SliceImpl<>(List.of(response), pageable, false);
        when(productRepository.search(condition, pageable)).thenReturn(mockSlice);

        Slice<ProductSummaryResponse> result = productService.getProducts(condition, pageable);

        assertThat(result.getContent()).containsExactly(response);
        verify(productRepository).search(condition, pageable);
    }
}