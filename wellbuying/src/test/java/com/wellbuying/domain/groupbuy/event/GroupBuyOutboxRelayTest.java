package com.wellbuying.domain.groupbuy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.event.GroupBuyOutboxDispatcher.DispatchFailure;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

// 배치 전체를 순차가 아니라 병렬로 발행하고, 성공/실패를 나눠 GroupBuyOutboxDispatcher에 위임하는지 검증한다
// (건별 DB 반영은 GroupBuyOutboxDispatcherTest가 다룬다)
@ExtendWith(MockitoExtension.class)
class GroupBuyOutboxRelayTest {

    @Mock
    private GroupBuyEventOutboxRepository outboxRepository;

    @Mock
    private GroupBuyOutboxDispatcher dispatcher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private GroupBuyOutboxRelay relay;

    // groupBuyId를 Kafka 메시지 키로 사용하므로, 이벤트별로 성공/실패 mock을 구분하려면 groupBuyId가 서로 달라야 한다
    private GroupBuyEventOutbox eventWithId(Long id) {
        GroupBuyEventOutbox event = GroupBuyEventOutbox.of(id, "GroupBuyCanceled", "{\"groupBuyId\":" + id + "}");
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    // 미발행 대상이 없으면 Kafka 발행도, DB 반영도 시도하지 않는지 검증
    @Test
    void 미발행_대상이_없으면_아무것도_하지_않는다() {
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(anyInt(), any()))
                .thenReturn(List.of());

        relay.relay();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(dispatcher, never()).markPublished(any());
        verify(dispatcher, never()).recordFailures(any());
    }

    // 여러 건을 순차 .get()이 아니라 병렬로 발행하고(모든 send가 개별적으로 호출됨), 성공/실패 결과를
    // 정확히 나눠 dispatcher.markPublished / dispatcher.recordFailures로 위임하는지 검증
    @SuppressWarnings("unchecked")
    @Test
    void 성공과_실패가_섞이면_결과를_나눠_dispatcher에_위임한다() {
        GroupBuyEventOutbox succeeded = eventWithId(1L);
        GroupBuyEventOutbox failed = eventWithId(2L);
        when(outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(
                eq(GroupBuyEventOutbox.MAX_RETRY_COUNT), any()))
                .thenReturn(List.of(succeeded, failed));

        CompletableFuture<SendResult<String, String>> successFuture =
                CompletableFuture.completedFuture(mock(SendResult.class));
        CompletableFuture<SendResult<String, String>> failureFuture = new CompletableFuture<>();
        failureFuture.completeExceptionally(new RuntimeException("브로커 연결 실패"));
        when(kafkaTemplate.send(anyString(), eq("1"), anyString())).thenReturn(successFuture);
        when(kafkaTemplate.send(anyString(), eq("2"), anyString())).thenReturn(failureFuture);

        relay.relay();

        ArgumentCaptor<List<GroupBuyEventOutbox>> succeededCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).markPublished(succeededCaptor.capture());
        assertThat(succeededCaptor.getValue()).containsExactly(succeeded);

        ArgumentCaptor<List<DispatchFailure>> failedCaptor = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).recordFailures(failedCaptor.capture());
        assertThat(failedCaptor.getValue()).extracting(DispatchFailure::event).containsExactly(failed);
    }
}
