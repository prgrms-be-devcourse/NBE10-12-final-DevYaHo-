package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.dto.GroupBuyProductSummaryResponse;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.service.GroupBuyService;
import java.time.LocalDateTime;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductCountRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProductSearchReconcileSchedulerTest {

    private static final PageRequest LIMIT = PageRequest.of(0, 500);

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private GroupBuyService groupBuyService;

    @Mock
    private ProductCountRepository productCountRepository;

    private SimpleMeterRegistry meterRegistry;
    private ProductSearchReconcileScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new ProductSearchReconcileScheduler(
                productRepository, productSearchRepository, groupBuyService, productCountRepository, meterRegistry);
    }

    @Test
    void reconcile_APPROVED_상품을_페이지_단위로_재색인한다() {
        Product p1 = mockProduct(1L);
        Product p2 = mockProduct(2L);
        Product p3 = mockProduct(3L);
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 0L, LIMIT)).thenReturn(List.of(p1, p2));
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 2L, LIMIT)).thenReturn(List.of(p3));
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 3L, LIMIT)).thenReturn(List.of());
        GroupBuyProductSummaryResponse summary =
                new GroupBuyProductSummaryResponse(100L, GroupBuyStatus.ONGOING, 8000, 5, 10, 100, LocalDateTime.of(2026, 9, 30, 23, 59));
        when(groupBuyService.getActiveSummariesByProductIds(List.of(1L, 2L))).thenReturn(Map.of());
        when(groupBuyService.getActiveSummariesByProductIds(List.of(3L))).thenReturn(Map.of(3L, summary));
        when(productCountRepository.findAllById(any())).thenReturn(List.of());

        scheduler.reconcile();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(productSearchRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        List<List<ProductSearchDocument>> allCalls = captor.getAllValues();
        assertThat(allCalls.get(0)).allMatch(doc -> !doc.hasActiveGroupBuy());
        assertThat(allCalls.get(1)).allMatch(doc -> doc.hasActiveGroupBuy());
        assertThat(meterRegistry.get("wellbuying.search.reconcile.documents").counter().count()).isEqualTo(3.0);
        assertThat(meterRegistry.get("wellbuying.search.reconcile.duration").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("wellbuying.search.reconcile.failures").counter().count()).isZero();
        assertThat(meterRegistry.get("wellbuying.search.reconcile.last_success_timestamp_seconds").gauge().value()).isGreaterThan(0.0);
    }

    @Test
    void reconcile_APPROVED_상품이_없으면_색인하지_않는다() {
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 0L, LIMIT)).thenReturn(List.of());

        scheduler.reconcile();

        verify(productSearchRepository, never()).saveAll(any());
        verify(groupBuyService, never()).getActiveSummariesByProductIds(any());
    }

    @Test
    void reconcile_중간_페이지_실패시_예외를_밖으로_던지지_않는다() {
        Product p1 = mockProduct(1L);
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 0L, LIMIT)).thenReturn(List.of(p1));
        when(groupBuyService.getActiveSummariesByProductIds(any())).thenReturn(Map.of());
        when(productCountRepository.findAllById(any())).thenReturn(List.of());
        when(productSearchRepository.saveAll(any())).thenThrow(new RuntimeException("OpenSearch 연결 실패"));

        assertThatCode(() -> scheduler.reconcile()).doesNotThrowAnyException();
        assertThat(meterRegistry.get("wellbuying.search.reconcile.failures").counter().count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("wellbuying.search.reconcile.last_success_timestamp_seconds").gauge().value()).isEqualTo(0.0);
    }

    @Test
    void reconcile_실패_후_재실행하면_마지막_성공_지점부터_이어서_처리한다() {
        Product p1 = mockProduct(1L);
        Product p2 = mockProduct(2L);
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 0L, LIMIT)).thenReturn(List.of(p1));
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 1L, LIMIT)).thenReturn(List.of(p2));
        when(productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                ProductStatus.APPROVED, 2L, LIMIT)).thenReturn(List.of());
        when(groupBuyService.getActiveSummariesByProductIds(List.of(1L))).thenReturn(Map.of());
        when(groupBuyService.getActiveSummariesByProductIds(List.of(2L))).thenReturn(Map.of());
        when(productCountRepository.findAllById(any())).thenReturn(List.of());
        // 1차: p1 saveAll 성공 → resumeFromId=1, p2 saveAll 실패 / 2차: p2 saveAll 성공
        when(productSearchRepository.saveAll(any()))
                .thenReturn(List.of())
                .thenThrow(new RuntimeException("OpenSearch 연결 실패"))
                .thenReturn(List.of());

        scheduler.reconcile(); // 1차: 0L→p1 성공(resumeFromId=1) → 1L→p2 실패
        scheduler.reconcile(); // 2차: resumeFromId=1에서 이어서 시작

        // findBy(1L)은 1차 1회 + 2차 1회 = 2회, findBy(0L)은 1차 1회만(2차는 0L에서 시작하지 않음)
        verify(productRepository, org.mockito.Mockito.times(2))
                .findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(ProductStatus.APPROVED, 1L, LIMIT);
        verify(productRepository, org.mockito.Mockito.times(1))
                .findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(ProductStatus.APPROVED, 0L, LIMIT);
    }

    private Product mockProduct(Long id) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getStatus()).thenReturn(ProductStatus.APPROVED);
        return product;
    }
}