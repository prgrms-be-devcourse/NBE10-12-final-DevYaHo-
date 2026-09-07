package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.product.search.ProductSearchOutboxDispatcher.DispatchFailure;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// ProductSearchOutboxRelay가 배치 전체를 ES에 반영한 결과(성공/실패)를 DB에 반영한다
// (ES 반영 자체는 ProductSearchOutboxRelayTest가 다룬다)
@ExtendWith(MockitoExtension.class)
class ProductSearchOutboxDispatcherTest {

    @Mock
    private ProductSearchEventOutboxRepository outboxRepository;

    @InjectMocks
    private ProductSearchOutboxDispatcher dispatcher;

    // 반영에 성공한 이벤트들의 id를 모아 한 번의 벌크 UPDATE(markPublished)로 반영하는지 검증
    @Test
    void markPublished_성공한_이벤트_id를_모아_한_번에_반영한다() {
        ProductSearchEventOutbox event1 = ProductSearchEventOutbox.upsert(1L);
        ProductSearchEventOutbox event2 = ProductSearchEventOutbox.upsert(2L);
        org.springframework.test.util.ReflectionTestUtils.setField(event1, "id", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(event2, "id", 20L);

        dispatcher.markPublished(List.of(event1, event2));

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markPublished(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L);
    }

    // 빈 목록이면 불필요한 UPDATE를 만들지 않는지 검증
    @Test
    void markPublished_빈_목록이면_아무것도_하지_않는다() {
        dispatcher.markPublished(List.of());

        verify(outboxRepository, never()).markPublished(anyList(), any());
    }

    // 반영에 실패한 이벤트들의 retryCount를 한 번의 벌크 UPDATE(incrementRetryCount)로 반영하는지 검증
    @Test
    void recordFailures_실패한_이벤트_id를_모아_retryCount를_한_번에_증가시킨다() {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.upsert(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", 10L);

        dispatcher.recordFailures(List.of(new DispatchFailure(event, new RuntimeException("실패"))));

        assertThat(event.getRetryCount()).isEqualTo(1);
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).incrementRetryCount(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(10L);
    }

    // 재시도 횟수가 MAX_RETRY_COUNT에 도달하면 poison pill로 간주해 재시도가 소진된 것으로 표시하는지 검증 -
    // ProductSearchOutboxRelay는 이 상태를 폴링 조건에서 제외해 더 이상 조회하지 않는다
    @Test
    void 최대_재시도_횟수에_도달하면_재시도가_소진된_것으로_표시한다() {
        ProductSearchEventOutbox event = ProductSearchEventOutbox.upsert(1L);
        DispatchFailure failure = new DispatchFailure(event, new RuntimeException("실패"));

        for (int i = 0; i < ProductSearchEventOutbox.MAX_RETRY_COUNT; i++) {
            dispatcher.recordFailures(List.of(failure));
        }

        assertThat(event.getRetryCount()).isEqualTo(ProductSearchEventOutbox.MAX_RETRY_COUNT);
        assertThat(event.isRetryExhausted()).isTrue();
    }

    // 빈 목록이면 아무것도 하지 않는지 검증
    @Test
    void recordFailures_빈_목록이면_아무것도_하지_않는다() {
        dispatcher.recordFailures(List.of());

        verify(outboxRepository, never()).incrementRetryCount(any());
    }
}
