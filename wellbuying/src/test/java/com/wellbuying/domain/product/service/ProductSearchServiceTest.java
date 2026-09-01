package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.product.search.ProductSearchRepository;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.search.SearchSortType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

// OpenSearch 없이 리포지토리를 Mock으로 대체해서, 서비스가 파라미터를 그대로 위임하는지만 검증
@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Test
    void search_파라미터를_리포지토리에_그대로_위임하고_결과를_반환한다() {
        ProductSearchService service = new ProductSearchService(productSearchRepository);
        ProductSearchResponse response = new ProductSearchResponse(1L, "비타민C", 5000, "url", 0L);
        Slice<ProductSearchResponse> mockSlice = new SliceImpl<>(List.of(response), PageRequest.of(0, 20), false);
        when(productSearchRepository.search("비타민", SearchSortType.RELEVANCE, 0, 20)).thenReturn(mockSlice);

        Slice<ProductSearchResponse> result = service.search("비타민", SearchSortType.RELEVANCE, 0, 20);

        assertThat(result.getContent()).containsExactly(response);
        verify(productSearchRepository).search("비타민", SearchSortType.RELEVANCE, 0, 20);
    }
}
