package com.wellbuying.domain.payment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.payment.entity.PaymentEventOutbox;
import com.wellbuying.domain.payment.repository.PaymentEventOutboxRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

// 아웃박스 패턴의 핵심 - Kafka로 직접 보내지 않고, 호출자 트랜잭션 안에서 아웃박스 행만 저장하는지 검증한다.
// (실제 Kafka 발행은 PaymentOutboxRelayTest / PaymentOutboxDispatcherTest가 다룬다)
class PaymentEventPublisherTest {

    private final PaymentEventOutboxRepository outboxRepository = mock(PaymentEventOutboxRepository.class);
    private final PaymentEventPublisher publisher = new PaymentEventPublisher(outboxRepository, new ObjectMapper());

    @Test
    void publishCompleted는_groupBuyId를_키로_이벤트타입과_직렬화된_페이로드를_담은_아웃박스_행을_저장한다() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(PaymentEventType.PAYMENT_COMPLETED.code(),
                100L, "gb-order-1", 42L, 7L, 5L, 3L, 10_000, "pay_abc", LocalDateTime.now());

        publisher.publishCompleted(event);

        PaymentEventOutbox saved = captureSaved();
        assertThat(saved.getGroupBuyId()).isEqualTo(42L);
        assertThat(saved.getEventType()).isEqualTo(PaymentEventType.PAYMENT_COMPLETED.code());
        assertThat(saved.getPayload()).contains("\"groupBuyId\":42").contains("pay_abc");
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(saved.getRetryCount()).isZero();
    }

    @Test
    void publishFailed도_같은_방식으로_아웃박스_행을_저장한다() {
        PaymentFailedEvent event = PaymentFailedEvent.of(100L, 42L, 7L, 5L, 10_000, "등록된 빌링키가 없음");

        publisher.publishFailed(event);

        PaymentEventOutbox saved = captureSaved();
        assertThat(saved.getGroupBuyId()).isEqualTo(42L);
        assertThat(saved.getEventType()).isEqualTo(PaymentEventType.PAYMENT_FAILED.code());
        assertThat(saved.getPayload()).contains("등록된 빌링키가 없음");
        assertThat(saved.getPublishedAt()).isNull();
    }

    private PaymentEventOutbox captureSaved() {
        ArgumentCaptor<PaymentEventOutbox> captor = ArgumentCaptor.forClass(PaymentEventOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }
}
