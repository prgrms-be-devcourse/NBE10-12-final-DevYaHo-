package com.wellbuying.domain.groupbuy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class GroupBuyOutboxDispatcherTest {

    @Mock
    private GroupBuyEventOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private GroupBuyOutboxDispatcher dispatcher;

    // 발행에 성공하면 publishedAt을 채워 다시 저장하는지 검증 - 이후 릴레이의 미발행 조회 대상에서 빠지게 된다
    @Test
    void 발행에_성공하면_publishedAt을_채워_저장한다() {
        GroupBuyEventOutbox event = GroupBuyEventOutbox.of(1L, "GroupBuyCanceled", "{\"groupBuyId\":1}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        dispatcher.dispatch(event);

        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(event);
    }

    // Kafka 발행이 실패하면(브로커 다운 등) publishedAt을 채우지 않고 그대로 둔다 - 저장도 하지 않아
    // 다음 릴레이 주기에 같은 행이 다시 미발행 상태로 조회되어 재시도된다(at-least-once)
    @Test
    void 발행에_실패하면_publishedAt을_채우지_않고_저장도_하지_않는다() {
        GroupBuyEventOutbox event = GroupBuyEventOutbox.of(1L, "GroupBuyCanceled", "{\"groupBuyId\":1}");
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("브로커 연결 실패"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        dispatcher.dispatch(event);

        assertThat(event.getPublishedAt()).isNull();
        verify(outboxRepository, org.mockito.Mockito.never()).save(any());
    }
}
