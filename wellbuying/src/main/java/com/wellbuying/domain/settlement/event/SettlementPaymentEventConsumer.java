package com.wellbuying.domain.settlement.event;

import com.wellbuying.domain.settlement.service.SettlementAccrualService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// payment-events 토픽(payment의 PaymentEventPublisher가 발행)을 구독해 정산 대상을 적립한다.
// 같은 토픽에 완료·실패가 함께 흐르므로 eventType으로 PaymentCompleted만 골라낸다 - 실패 건은 정산 대상이 아니다.
// eventType 문자열은 domain.payment.event.PaymentEventType의 code()와 같은 값의 wire contract이며,
// 발행 측 도메인 클래스를 직접 참조하지 않기 위해 상수로만 매칭한다(notification.PaymentEventConsumer와 동일).
@Component
public class SettlementPaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SettlementPaymentEventConsumer.class);

    private static final String EVENT_TYPE_COMPLETED = "PaymentCompleted";

    private final ObjectMapper objectMapper;
    private final SettlementAccrualService settlementAccrualService;

    public SettlementPaymentEventConsumer(ObjectMapper objectMapper,
            SettlementAccrualService settlementAccrualService) {
        this.objectMapper = objectMapper;
        this.settlementAccrualService = settlementAccrualService;
    }

    @KafkaListener(topics = "payment-events", groupId = "settlement-service")
    public void onMessage(String payload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            // 형식이 깨진 메시지는 재시도해도 성공할 수 없는 poison message이므로 소비를 끝내고 로그만 남긴다
            // (notification.PaymentEventConsumer와 동일한 취지)
            log.error("payment-events 페이로드 파싱 실패 - 메시지를 건너뜀. payload: {}", payload, e);
            return;
        }

        String eventType = root.path("eventType").asString();
        if (!EVENT_TYPE_COMPLETED.equals(eventType)) {
            log.debug("정산 대상이 아닌 이벤트 타입이라 무시함: {}", eventType);
            return;
        }

        PaymentCompletedMessage message;
        try {
            message = objectMapper.treeToValue(root, PaymentCompletedMessage.class);
        } catch (Exception e) {
            log.error("PaymentCompleted 역직렬화 실패 - 메시지를 건너뜀. payload: {}", payload, e);
            return;
        }

        settlementAccrualService.accrue(message);
    }
}
