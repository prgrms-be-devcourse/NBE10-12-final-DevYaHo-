package com.wellbuying.domain.notification.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

// eventType에 따라 알맞은 NotificationService 메서드로 분기하는지만 검증 - 실제 저장 로직은 NotificationServiceTest가 다룬다
class PaymentEventConsumerTest {

    @Test
    void PaymentCompleted_이벤트는_notifyPaymentCompleted로_위임한다() {
        NotificationService notificationService = mock(NotificationService.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(notificationService, new ObjectMapper());
        String payload = """
                {"eventType":"PaymentCompleted","paymentId":1,"orderId":"o-1","groupBuyId":1,
                "groupBuyParticipantId":50,"memberId":100,"producerId":5,"amount":10000,
                "pgTransactionId":"tx-1","occurredAt":"2026-01-01T00:00:00"}
                """;

        consumer.onMessage(payload);

        verify(notificationService, times(1)).notifyPaymentCompleted(new PaymentCompletedPayload(1L, 100L));
    }

    @Test
    void PaymentFailed_이벤트는_notifyPaymentFailed로_위임한다() {
        NotificationService notificationService = mock(NotificationService.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(notificationService, new ObjectMapper());
        String payload = """
                {"eventType":"PaymentFailed","paymentId":1,"groupBuyId":1,"groupBuyParticipantId":50,
                "memberId":100,"amount":10000,"reason":"등록된 빌링키가 없음","occurredAt":"2026-01-01T00:00:00"}
                """;

        consumer.onMessage(payload);

        verify(notificationService, times(1)).notifyPaymentFailed(new PaymentFailedPayload(1L, 100L));
        verify(notificationService, never()).notifyPaymentCompleted(any());
    }

    @Test
    void 알림_대상이_아닌_이벤트_타입은_무시한다() {
        NotificationService notificationService = mock(NotificationService.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(notificationService, new ObjectMapper());
        String payload = """
                {"eventType":"SomethingElse","groupBuyId":1,"memberId":100}
                """;

        consumer.onMessage(payload);

        verify(notificationService, never()).notifyPaymentCompleted(any());
        verify(notificationService, never()).notifyPaymentFailed(any());
    }

    // 형식이 깨진 메시지(poison message)는 재시도해도 성공할 수 없으므로, 예외를 밖으로 던져
    // Kafka 컨슈머를 무한 재시도에 빠뜨리는 대신 여기서 잡아 소비를 끝내야 한다
    @Test
    void 형식이_깨진_페이로드는_예외를_던지지_않고_건너뛴다() {
        NotificationService notificationService = mock(NotificationService.class);
        PaymentEventConsumer consumer = new PaymentEventConsumer(notificationService, new ObjectMapper());

        consumer.onMessage("{이것은-유효한-JSON이-아님");

        verify(notificationService, never()).notifyPaymentCompleted(any());
        verify(notificationService, never()).notifyPaymentFailed(any());
    }
}
