package com.wellbuying.domain.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.payment.entity.PaymentEventOutbox;
import com.wellbuying.domain.payment.event.PaymentOutboxDispatcher.DispatchFailure;
import com.wellbuying.domain.payment.repository.PaymentEventOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// PaymentOutboxRelay가 배치 전체를 병렬로 Kafka에 발행한 뒤 그 결과(성공/실패)를 넘겨주면,
// 이 클래스는 Kafka와 무관하게 DB 반영(벌크 UPDATE)만 담당한다 (Kafka 발행 자체는 PaymentOutboxRelayTest가 다룬다)
@ExtendWith(MockitoExtension.class)
class PaymentOutboxDispatcherTest {

    @Mock
    private PaymentEventOutboxRepository outboxRepository;

    @InjectMocks
    private PaymentOutboxDispatcher dispatcher;

    private PaymentEventOutbox eventWithId(Long groupBuyId, Long id) {
        PaymentEventOutbox event = PaymentEventOutbox.of(groupBuyId, "PaymentCompleted",
                "{\"groupBuyId\":" + groupBuyId + "}");
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    // 발행에 성공한 이벤트들의 id를 모아 한 번의 벌크 UPDATE(markPublished)로 반영하는지 검증
    @Test
    void markPublished는_성공한_이벤트_id를_모아_한_번에_반영한다() {
        PaymentEventOutbox event1 = eventWithId(1L, 10L);
        PaymentEventOutbox event2 = eventWithId(2L, 20L);

        dispatcher.markPublished(List.of(event1, event2));

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markPublished(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void markPublished는_빈_목록이면_아무것도_하지_않는다() {
        dispatcher.markPublished(List.of());

        verify(outboxRepository, never()).markPublished(anyList(), any());
    }

    // 발행에 실패한 이벤트들의 retryCount를 한 번의 벌크 UPDATE(incrementRetryCount)로 반영하는지 검증
    @Test
    void recordFailures는_실패한_이벤트_id를_모아_retryCount를_한_번에_증가시킨다() {
        PaymentEventOutbox event = eventWithId(1L, 10L);

        dispatcher.recordFailures(List.of(new DispatchFailure(event, new RuntimeException("브로커 연결 실패"))));

        assertThat(event.getRetryCount()).isEqualTo(1);
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).incrementRetryCount(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(10L);
    }

    // 재시도 횟수가 MAX_RETRY_COUNT에 도달하면 poison pill로 간주해 재시도가 소진된 것으로 표시하는지 검증 -
    // PaymentOutboxRelay는 이 상태를 폴링 조건에서 제외해 더 이상 조회하지 않는다
    @Test
    void 최대_재시도_횟수에_도달하면_재시도가_소진된_것으로_표시한다() {
        PaymentEventOutbox event = eventWithId(1L, 10L);
        DispatchFailure failure = new DispatchFailure(event, new RuntimeException("브로커 연결 실패"));

        for (int i = 0; i < PaymentEventOutbox.MAX_RETRY_COUNT; i++) {
            dispatcher.recordFailures(List.of(failure));
        }

        assertThat(event.getRetryCount()).isEqualTo(PaymentEventOutbox.MAX_RETRY_COUNT);
        assertThat(event.isRetryExhausted()).isTrue();
    }

    @Test
    void recordFailures는_빈_목록이면_아무것도_하지_않는다() {
        dispatcher.recordFailures(List.of());

        verify(outboxRepository, never()).incrementRetryCount(anyList());
    }
}
