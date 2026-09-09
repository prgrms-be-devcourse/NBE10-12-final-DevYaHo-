package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.domain.product.search.ProductSearchOutboxDispatcher.DispatchFailure;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// ProductSearchOutboxRelay가 pending 이벤트를 ES에 반영하고 성공/실패를 ProductSearchOutboxDispatcher에 위임하는지 검증
// (건별 DB 반영은 ProductSearchOutboxDispatcherTest가 다룬다)
@ExtendWith(MockitoExtension.class)
class ProductSearchOutboxRelayTest {

    @Mock
    private ProductSearchEventOutboxRepository outboxRepository;

    @Mock
    private ProductSearchOutboxDispatcher dispatcher;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSearchRepository productSearchRepository;

    @Mock
    private GroupBuyService groupBuyService;

    @InjectMocks
    private ProductSearchOutboxRelay relay;

    // 폴링 결과가 비어 있으면 ES 반영도 dispatcher 위임도 하지 않는지 검증
    @Test
    void relay_미반영_이벤트가_없으면_아무것도_하지_않는다() {
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of());

        relay.relay();

        verify(dispatcher, never()).markPublished(any());
        verify(dispatcher, never()).recordFailures(any());
    }

    // DELETE 이벤트는 상품 재조회 없이 바로 인덱스에서 제거하고 succeeded로 분류되는지 검증
    @Test
    void relay_DELETE_이벤트는_인덱스에서_바로_삭제하고_성공_처리한다() {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.delete(1L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of(event));

        relay.relay();

        verify(productSearchRepository).deleteById(1L);
        verify(productRepository, never()).findByIdInAndDeletedAtIsNull(any());
        ArgumentCaptor<List<ProductSearchEventOutbox>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).markPublished(captor.capture());
        assertThat(captor.getValue()).containsExactly(event);
    }

    // UPSERT 이벤트 - 상품이 존재하면 최신 상태를 인덱스에 upsert하고 succeeded로 분류되는지 검증
    @Test
    void relay_UPSERT_이벤트는_상품을_재조회해서_존재하면_인덱스에_저장한다() {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.upsert(1L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of(event));
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.APPROVED);
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(product));
        when(groupBuyService.getActiveSummariesByProductIds(any())).thenReturn(Map.of());

        relay.relay();

        verify(productSearchRepository).save(any(ProductSearchDocument.class));
        ArgumentCaptor<List<ProductSearchEventOutbox>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).markPublished(captor.capture());
        assertThat(captor.getValue()).containsExactly(event);
    }

    // Zombie Document 방지 - 재조회 시 이미 삭제된 상품이면 save 대신 deleteById로 처리되는지 검증
    @Test
    void relay_UPSERT_이벤트인데_상품이_이미_삭제됐으면_인덱스에서_제거한다() {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.upsert(1L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of(event));
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of());

        relay.relay();

        verify(productSearchRepository).deleteById(1L);
        verify(productSearchRepository, never()).save(any());
        ArgumentCaptor<List<ProductSearchEventOutbox>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).markPublished(captor.capture());
        assertThat(captor.getValue()).containsExactly(event);
    }

    // 승인된 상품만 색인 정책 - 재조회 시 상품이 존재해도 APPROVED가 아니면
    // (PENDING/REJECTED 등) save 대신 deleteById로 인덱스에서 제거되는지 검증
    // Zombie Document 방지와 동일하게 ifPresentOrElse의 orElse 분기를 타야 한다
    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void relay_UPSERT_이벤트인데_상품이_미승인_상태면_인덱스에서_제거한다(ProductStatus status) {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.upsert(1L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of(event));
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(status);
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(product));

        relay.relay();

        verify(productSearchRepository).deleteById(1L);
        verify(productSearchRepository, never()).save(any());
        ArgumentCaptor<List<ProductSearchEventOutbox>> captor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).markPublished(captor.capture());
        assertThat(captor.getValue()).containsExactly(event);
    }

    // ES 반영 중 예외가 나면 해당 이벤트만 failures로 분류되고 밖으로 전파되지 않는지 검증
    @Test
    void relay_반영_도중_예외가_나면_실패로_분류해서_dispatcher에_넘긴다() {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.upsert(1L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of(event));
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.APPROVED);
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(product));
        when(groupBuyService.getActiveSummariesByProductIds(any())).thenReturn(Map.of());
        when(productSearchRepository.save(any())).thenThrow(new RuntimeException("OpenSearch 연결 실패"));

        relay.relay();

        ArgumentCaptor<List<ProductSearchEventOutbox>> succeededCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).markPublished(succeededCaptor.capture());
        assertThat(succeededCaptor.getValue()).isEmpty();
        ArgumentCaptor<List<DispatchFailure>> failedCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).recordFailures(failedCaptor.capture());
        assertThat(failedCaptor.getValue()).extracting(DispatchFailure::event).containsExactly(event);
    }

    // UPSERT 이벤트에서 공동구매 요약 Map에 해당 상품이 있으면 문서에 hasActiveGroupBuy=true와 요약 값이 반영되는지 검증
    @Test
    void relay_UPSERT_이벤트에_공동구매_요약이_있으면_문서에_반영된다() {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.upsert(1L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of(event));
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.APPROVED);
        when(productRepository.findByIdInAndDeletedAtIsNull(List.of(1L))).thenReturn(List.of(product));
        GroupBuyProductSummaryResponse summary =
                new GroupBuyProductSummaryResponse(100L, GroupBuyStatus.ONGOING, 8000, 5, 10, 100, LocalDateTime.of(2026, 9, 30, 23, 59));
        when(groupBuyService.getActiveSummariesByProductIds(List.of(1L))).thenReturn(Map.of(1L, summary));

        relay.relay();

        ArgumentCaptor<ProductSearchDocument> docCaptor = ArgumentCaptor.forClass(ProductSearchDocument.class);
        verify(productSearchRepository).save(docCaptor.capture());
        assertThat(docCaptor.getValue().hasActiveGroupBuy()).isTrue();
        assertThat(docCaptor.getValue().currentUnitPrice()).isEqualTo(8000);
    }

    // 폴링 자체가 실패해도(DB 커넥션 문제 등) @Scheduled가 다음 주기에 재실행될 수 있도록
    // 예외가 relay() 밖으로 전파되지 않는지 검증
    @Test
    void relay_폴링_자체에서_인프라_예외가_나도_밖으로_전파되지_않는다() {
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenThrow(new RuntimeException("DB 커넥션 실패"));

        assertThatCode(() -> relay.relay()).doesNotThrowAnyException();

        verify(dispatcher, never()).markPublished(any());
        verify(dispatcher, never()).recordFailures(any());
    }
}
