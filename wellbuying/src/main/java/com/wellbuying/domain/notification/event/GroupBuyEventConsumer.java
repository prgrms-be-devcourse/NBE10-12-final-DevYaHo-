package com.wellbuying.domain.notification.event;

import com.wellbuying.domain.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// groupbuy-events 토픽(GroupBuyOutboxRelay가 발행)을 구독해 참여자 알림을 생성한다.
// eventType 문자열은 domain.groupbuy.event.GroupBuyEventType의 code()와 동일한 값의 wire contract이며,
// 발행 측 도메인 클래스를 직접 참조하지 않기 위해 상수로만 매칭한다.
@Component
public class GroupBuyEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyEventConsumer.class);

    private static final String EVENT_TYPE_COMPLETED = "GroupBuyCompleted";
    private static final String EVENT_TYPE_FAILED = "GroupBuyFailed";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public GroupBuyEventConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "groupbuy-events", groupId = "notification-service")
    public void onMessage(String payload) {
        String eventType = objectMapper.readValue(payload, GroupBuyEventEnvelope.class).eventType();

        switch (eventType) {
            case EVENT_TYPE_COMPLETED ->
                    notificationService.notifyCompleted(objectMapper.readValue(payload, GroupBuyCompletedPayload.class));
            case EVENT_TYPE_FAILED ->
                    notificationService.notifyFailed(objectMapper.readValue(payload, GroupBuyFailedPayload.class));
            // GroupBuyCanceled는 시작 전(참여자 없음) 상태에서만 발생하므로 알림 대상이 없어 무시한다
            default -> log.debug("알림 대상이 아닌 이벤트 타입이라 무시함: {}", eventType);
        }
    }
}
