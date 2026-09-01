package com.wellbuying.domain.groupbuy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.event.GroupBuyOutboxDispatcher.DispatchFailure;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// GroupBuyOutboxRelay가 배치 전체를 병렬로 Kafka에 발행한 뒤 그 결과(성공/실패)를 넘겨주면,
// 이 클래스는 Kafka와 무관하게 DB 반영(벌크 UPDATE)만 담당한다 (Kafka 발행 자체는 GroupBuyOutboxRelayTest가 다룬다)
@ExtendWith(MockitoExtension.class)
class GroupBuyOutboxDispatcherTest {

    @Mock
    private GroupBuyEventOutboxRepository outboxRepository;

    @InjectMocks
    private GroupBuyOutboxDispatcher dispatcher;

    // 발행에 성공한 이벤트들의 id를 모아 한 번의 벌크 UPDATE(markPublished)로 반영하는지 검증
    @Test
    void markPublished는_성공한_이벤트_id를_모아_한_번에_반영한다() {
        GroupBuyEventOutbox event1 = GroupBuyEventOutbox.of(1L, "GroupBuyCanceled", "{\"groupBuyId\":1}");
        GroupBuyEventOutbox event2 = GroupBuyEventOutbox.of(2L, "GroupBuyCanceled", "{\"groupBuyId\":2}");
        org.springframework.test.util.ReflectionTestUtils.setField(event1, "id", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(event2, "id", 20L);

        dispatcher.markPublished(List.of(event1, event2));

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).markPublished(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(10L, 20L);
    }

    // 빈 목록이면 아무 것도 하지 않는지 검증 (배치 전체가 성공/실패 한쪽으로 쏠린 경우 불필요한 UPDATE를 만들지 않음)
    @Test
    void markPublished는_빈_목록이면_아무것도_하지_않는다() {
        dispatcher.markPublished(List.of());

        verify(outboxRepository, never()).markPublished(anyList(), any());
    }

    // 발행에 실패한 이벤트들의 retryCount를 한 번의 벌크 UPDATE(incrementRetryCount)로 반영하는지 검증
    @Test
    void recordFailures는_실패한_이벤트_id를_모아_retryCount를_한_번에_증가시킨다() {
        GroupBuyEventOutbox event = GroupBuyEventOutbox.of(1L, "GroupBuyCanceled", "{\"groupBuyId\":1}");
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", 10L);

        dispatcher.recordFailures(List.of(new DispatchFailure(event, new RuntimeException("브로커 연결 실패"))));

        assertThat(event.getRetryCount()).isEqualTo(1);
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxRepository).incrementRetryCount(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactly(10L);
    }

    // 재시도 횟수가 MAX_RETRY_COUNT에 도달하면 poison pill로 간주해 재시도가 소진된 것으로 표시하는지 검증 -
    // GroupBuyOutboxRelay는 이 상태를 폴링 조건에서 제외해 더 이상 조회하지 않는다
    @Test
    void 최대_재시도_횟수에_도달하면_재시도가_소진된_것으로_표시한다() {
        GroupBuyEventOutbox event = GroupBuyEventOutbox.of(1L, "GroupBuyCanceled", "{\"groupBuyId\":1}");
        DispatchFailure failure = new DispatchFailure(event, new RuntimeException("브로커 연결 실패"));

        for (int i = 0; i < GroupBuyEventOutbox.MAX_RETRY_COUNT; i++) {
            dispatcher.recordFailures(List.of(failure));
        }

        assertThat(event.getRetryCount()).isEqualTo(GroupBuyEventOutbox.MAX_RETRY_COUNT);
        assertThat(event.isRetryExhausted()).isTrue();
    }

    // 빈 목록이면 아무 것도 하지 않는지 검증
    @Test
    void recordFailures는_빈_목록이면_아무것도_하지_않는다() {
        dispatcher.recordFailures(List.of());

        verify(outboxRepository, never()).incrementRetryCount(anyList());
    }
}
