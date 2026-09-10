package com.wellbuying.domain.notification.event;

import com.wellbuying.domain.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// payment-events 토픽(PaymentEventPublisher가 발행)을 구독해 결제 완료 알림을 생성한다.
// eventType 문자열은 domain.payment.event.PaymentEventType의 code()와 동일한 값의 wire contract이며,
// 발행 측 도메인 클래스를 직접 참조하지 않기 위해 상수로만 매칭한다.
// 알림 도메인에도 groupbuy-events를 듣는 GroupBuyEventConsumer가 있어 이름이 겹친다 - 도메인+토픽으로
// 구분한다(payment 도메인의 PaymentGroupBuyEventConsumer와 반대 방향의 이름 짓기 패턴).
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private static final String EVENT_TYPE_COMPLETED = "PaymentCompleted";
    private static final String EVENT_TYPE_FAILED = "PaymentFailed";

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events", groupId = "notification-service")
    public void onMessage(String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            // 형식이 깨진 메시지는 재시도해도 절대 성공할 수 없는 poison message이므로 소비를 끝내고
            // 로그만 남긴다 (GroupBuyEventConsumer와 동일한 취지)
            log.error("payment-events 페이로드 파싱 실패 - 메시지를 건너뜀. payload: {}", payload, e);
            return;
        }

        String eventType = root.path("eventType").asString();
        switch (eventType) {
            case EVENT_TYPE_COMPLETED ->
                    notificationService.notifyPaymentCompleted(objectMapper.treeToValue(root, PaymentCompletedPayload.class));
            case EVENT_TYPE_FAILED ->
                    notificationService.notifyPaymentFailed(objectMapper.treeToValue(root, PaymentFailedPayload.class));
            default -> log.debug("알림 대상이 아닌 이벤트 타입이라 무시함: {}", eventType);
        }
    }
}
