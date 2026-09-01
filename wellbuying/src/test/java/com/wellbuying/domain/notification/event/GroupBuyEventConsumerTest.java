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
class GroupBuyEventConsumerTest {

    @Test
    void GroupBuyCompleted_이벤트는_notifyCompleted로_위임한다() {
        NotificationService notificationService = mock(NotificationService.class);
        GroupBuyEventConsumer consumer = new GroupBuyEventConsumer(notificationService, new ObjectMapper());
        String payload = """
                {"eventType":"GroupBuyCompleted","groupBuyId":1,"productId":10,"producerId":5,
                "partId":50,"memberId":100,"quantity":2,"appliedPrice":1000,"occurredAt":"2026-01-01T00:00:00"}
                """;

        consumer.onMessage(payload);

        verify(notificationService, times(1)).notifyCompleted(
                new GroupBuyCompletedPayload(1L, 10L, 100L));
        verify(notificationService, never()).notifyFailed(any());
    }

    @Test
    void GroupBuyFailed_이벤트는_notifyFailed로_위임한다() {
        NotificationService notificationService = mock(NotificationService.class);
        GroupBuyEventConsumer consumer = new GroupBuyEventConsumer(notificationService, new ObjectMapper());
        String payload = """
                {"eventType":"GroupBuyFailed","groupBuyId":1,"productId":10,"producerId":5,
                "currentQuantity":3,"minQuantity":10,"occurredAt":"2026-01-01T00:00:00"}
                """;

        consumer.onMessage(payload);

        verify(notificationService, times(1)).notifyFailed(new GroupBuyFailedPayload(1L, 10L));
        verify(notificationService, never()).notifyCompleted(any());
    }

    @Test
    void GroupBuyCanceled_이벤트는_무시한다() {
        NotificationService notificationService = mock(NotificationService.class);
        GroupBuyEventConsumer consumer = new GroupBuyEventConsumer(notificationService, new ObjectMapper());
        String payload = """
                {"eventType":"GroupBuyCanceled","groupBuyId":1,"productId":10,"producerId":5,
                "occurredAt":"2026-01-01T00:00:00"}
                """;

        consumer.onMessage(payload);

        verify(notificationService, never()).notifyCompleted(any());
        verify(notificationService, never()).notifyFailed(any());
    }

    // 형식이 깨진 메시지(poison message)는 재시도해도 성공할 수 없으므로, 예외를 밖으로 던져
    // Kafka 컨슈머를 무한 재시도에 빠뜨리는 대신 여기서 잡아 소비를 끝내야 한다
    @Test
    void 형식이_깨진_페이로드는_예외를_던지지_않고_건너뛴다() {
        NotificationService notificationService = mock(NotificationService.class);
        GroupBuyEventConsumer consumer = new GroupBuyEventConsumer(notificationService, new ObjectMapper());

        consumer.onMessage("{이것은-유효한-JSON이-아님");

        verify(notificationService, never()).notifyCompleted(any());
        verify(notificationService, never()).notifyFailed(any());
    }
}
