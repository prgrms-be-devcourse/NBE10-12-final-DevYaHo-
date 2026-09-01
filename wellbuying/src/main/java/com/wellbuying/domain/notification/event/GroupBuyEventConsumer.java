package com.wellbuying.domain.notification.event;

import com.wellbuying.domain.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
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
        JsonNode root;
        try {
            // eventType 분기와 구체 타입 역직렬화를 트리 하나로 처리해 payload를 두 번 파싱하지 않는다
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            // 형식이 깨진 메시지는 재시도해도 절대 성공할 수 없는 poison message이므로,
            // (아웃박스의 MAX_RETRY_COUNT와 같은 취지로) 여기서 소비를 끝내고 로그만 남긴다.
            // notifyCompleted/notifyFailed 실행 중 예외(예: DB 일시 장애)는 여기서 잡지 않고 그대로
            // 던져서 Kafka가 커밋하지 않고 재시도하도록 둔다 - 그런 경우는 재시도하면 성공할 수 있어서다
            log.error("groupbuy-events 페이로드 파싱 실패 - 메시지를 건너뜀. payload: {}", payload, e);
            return;
        }

        String eventType = root.path("eventType").asString();
        switch (eventType) {
            case EVENT_TYPE_COMPLETED ->
                    notificationService.notifyCompleted(objectMapper.treeToValue(root, GroupBuyCompletedPayload.class));
            case EVENT_TYPE_FAILED ->
                    notificationService.notifyFailed(objectMapper.treeToValue(root, GroupBuyFailedPayload.class));
            // GroupBuyCanceled는 시작 전(참여자 없음) 상태에서만 발생하므로 알림 대상이 없어 무시한다
            default -> log.debug("알림 대상이 아닌 이벤트 타입이라 무시함: {}", eventType);
        }
    }
}
