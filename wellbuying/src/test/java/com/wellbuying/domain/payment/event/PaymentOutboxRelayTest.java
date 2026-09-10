package com.wellbuying.domain.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.payment.entity.PaymentEventOutbox;
import com.wellbuying.domain.payment.event.PaymentOutboxDispatcher.DispatchFailure;
import com.wellbuying.domain.payment.repository.PaymentEventOutboxRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

// 배치 전체를 순차가 아니라 병렬로 payment-events에 발행하고, 성공/실패를 나눠 PaymentOutboxDispatcher에 위임하는지 검증한다
// (건별 DB 반영은 PaymentOutboxDispatcherTest가 다룬다)
@ExtendWith(MockitoExtension.class)
class PaymentOutboxRelayTest {

    @Mock
    private PaymentEventOutboxRepository outboxRepository;

    @Mock
    private PaymentOutboxDispatcher dispatcher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private PaymentOutboxRelay relay;

    // groupBuyId를 Kafka 메시지 키로 사용하므로, 이벤트별로 성공/실패 mock을 구분하려면 groupBuyId가 서로 달라야 한다
    private PaymentEventOutbox eventWithId(Long id) {
        PaymentEventOutbox event = PaymentEventOutbox.of(id, "PaymentCompleted", "{\"groupBuyId\":" + id + "}");
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Test
    void 미발행_대상이_없으면_아무것도_하지_않는다() {
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of());

        relay.relay();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(dispatcher, never()).markPublished(any());
        verify(dispatcher, never()).recordFailures(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void 성공과_실패가_섞이면_결과를_나눠_dispatcher에_위임한다() {
        PaymentEventOutbox succeeded = eventWithId(1L);
        PaymentEventOutbox failed = eventWithId(2L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(
                eq(PaymentEventOutbox.MAX_RETRY_COUNT), any()))
                .thenReturn(List.of(succeeded, failed));

        CompletableFuture<SendResult<String, String>> successFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
        CompletableFuture<SendResult<String, String>> failureFuture = new CompletableFuture<>();
        failureFuture.completeExceptionally(new RuntimeException("브로커 연결 실패"));
        when(kafkaTemplate.send(anyString(), eq("1"), anyString())).thenReturn(successFuture);
        when(kafkaTemplate.send(anyString(), eq("2"), anyString())).thenReturn(failureFuture);

        relay.relay();

        ArgumentCaptor<List<PaymentEventOutbox>> succeededCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).markPublished(succeededCaptor.capture());
        assertThat(succeededCaptor.getValue()).containsExactly(succeeded);

        ArgumentCaptor<List<DispatchFailure>> failedCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).recordFailures(failedCaptor.capture());
        assertThat(failedCaptor.getValue()).extracting(DispatchFailure::event).containsExactly(failed);
    }

    // 조회/DB 반영 중 예외가 나도(DB 커넥션 문제 등) 이 메서드 밖으로 전파되지 않고 잡히는지 검증 -
    // 그래야 @Scheduled(fixedDelay = 3_000)가 다음 주기에도 정상적으로 재실행된다
    @Test
    void 배치_처리_중_예외가_나도_밖으로_전파되지_않는다() {
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenThrow(new RuntimeException("DB 커넥션 실패"));

        org.assertj.core.api.Assertions.assertThatCode(() -> relay.relay()).doesNotThrowAnyException();
    }

    // 한 라운드가 BATCH_LIMIT(200)만큼 꽉 차면 다음 3초 틱을 기다리지 않고 이번 틱 안에서 곧바로 다음 라운드를 이어서 처리하는지 검증
    @SuppressWarnings("unchecked")
    @Test
    void 라운드가_가득_차면_같은_틱_안에서_다음_라운드를_이어서_처리한다() {
        List<PaymentEventOutbox> fullRound = IntStream.rangeClosed(1, 200)
                .mapToObj(i -> eventWithId((long) i))
                .toList();
        List<PaymentEventOutbox> partialRound = List.of(eventWithId(201L));
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(fullRound, partialRound);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        verify(outboxRepository, times(2))
                .findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any());
        ArgumentCaptor<List<PaymentEventOutbox>> succeededCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher, times(2)).markPublished(succeededCaptor.capture());
        assertThat(succeededCaptor.getAllValues().get(0)).hasSize(200);
        assertThat(succeededCaptor.getAllValues().get(1)).hasSize(1);
    }

    // 한 라운드에서 단 한 건도 발행에 성공하지 못하면(브로커 다운 등) 다음 라운드로 이어가지 않고 그 틱을 종료하는지 검증
    @SuppressWarnings("unchecked")
    @Test
    void 라운드_전체가_실패하면_같은_틱_안에서_재시도하지_않는다() {
        List<PaymentEventOutbox> fullRound = IntStream.rangeClosed(1, 200)
                .mapToObj(i -> eventWithId((long) i))
                .toList();
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(fullRound);
        CompletableFuture<SendResult<String, String>> brokerDown = new CompletableFuture<>();
        brokerDown.completeExceptionally(new RuntimeException("브로커 연결 불가"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(brokerDown);

        relay.relay();

        verify(outboxRepository, times(1))
                .findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any());
        ArgumentCaptor<List<PaymentEventOutbox>> succeededCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher, times(1)).markPublished(succeededCaptor.capture());
        assertThat(succeededCaptor.getValue()).isEmpty();
        ArgumentCaptor<List<DispatchFailure>> failedCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher, times(1)).recordFailures(failedCaptor.capture());
        assertThat(failedCaptor.getValue()).hasSize(200);
    }
}
