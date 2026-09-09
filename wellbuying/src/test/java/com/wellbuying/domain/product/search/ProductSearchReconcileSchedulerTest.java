package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.dto.GroupBuyProductSummaryResponse;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.service.GroupBuyService;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ProductSearchReconcileSchedulerTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private GroupBuyService groupBuyService;

    @InjectMocks
    private ProductSearchReconcileScheduler scheduler;

    @Test
    void reconcile_APPROVED_상품을_페이지_단위로_재색인한다() {
        Product p1 = mockProduct(1L);
        Product p2 = mockProduct(2L);
        Product p3 = mockProduct(3L);
        PageRequest req0 = PageRequest.of(0, 500, Sort.by("id"));
        PageRequest req1 = PageRequest.of(1, 500, Sort.by("id"));
        @SuppressWarnings("unchecked")
        Page<Product> page0 = mock(Page.class);
        when(page0.getContent()).thenReturn(List.of(p1, p2));
        when(page0.hasNext()).thenReturn(true);
        @SuppressWarnings("unchecked")
        Page<Product> page1 = mock(Page.class);
        when(page1.getContent()).thenReturn(List.of(p3));
        when(page1.hasNext()).thenReturn(false);
        when(productRepository.findByStatusAndDeletedAtIsNull(ProductStatus.APPROVED, req0)).thenReturn(page0);
        when(productRepository.findByStatusAndDeletedAtIsNull(ProductStatus.APPROVED, req1)).thenReturn(page1);
        GroupBuyProductSummaryResponse summary =
                new GroupBuyProductSummaryResponse(GroupBuyStatus.ONGOING, 8000, 5, 10);
        when(groupBuyService.getActiveSummariesByProductIds(List.of(1L, 2L))).thenReturn(Map.of());
        when(groupBuyService.getActiveSummariesByProductIds(List.of(3L))).thenReturn(Map.of(3L, summary));

        scheduler.reconcile();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(productSearchRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        List<List<ProductSearchDocument>> allCalls = captor.getAllValues();
        assertThat(allCalls.get(0)).allMatch(doc -> !doc.hasActiveGroupBuy());
        assertThat(allCalls.get(1)).allMatch(doc -> doc.hasActiveGroupBuy());
    }

    @Test
    void reconcile_APPROVED_상품이_없으면_색인하지_않는다() {
        PageRequest req0 = PageRequest.of(0, 500, Sort.by("id"));
        when(productRepository.findByStatusAndDeletedAtIsNull(ProductStatus.APPROVED, req0))
                .thenReturn(new PageImpl<>(List.of(), req0, 0));

        scheduler.reconcile();

        verify(productSearchRepository, never()).saveAll(any());
        verify(groupBuyService, never()).getActiveSummariesByProductIds(any());
    }

    @Test
    void reconcile_중간_페이지_실패시_예외를_밖으로_던지지_않는다() {
        Product p1 = mockProduct(1L);
        PageRequest req0 = PageRequest.of(0, 500, Sort.by("id"));
        when(productRepository.findByStatusAndDeletedAtIsNull(ProductStatus.APPROVED, req0))
                .thenReturn(new PageImpl<>(List.of(p1), req0, 1));
        when(groupBuyService.getActiveSummariesByProductIds(any())).thenReturn(Map.of());
        when(productSearchRepository.saveAll(any())).thenThrow(new RuntimeException("OpenSearch 연결 실패"));

        assertThatCode(() -> scheduler.reconcile()).doesNotThrowAnyException();
    }

    private Product mockProduct(Long id) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getStatus()).thenReturn(ProductStatus.APPROVED);
        return product;
    }
}