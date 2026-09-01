package com.wellbuying.domain.payment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// GroupBuyEventPublisher와 같은 방식 - Outbox 없이 KafkaTemplate으로 직접 발행한다
// (Phase1 범위: 발행 실패 시 유실은 감수, 재처리는 03-outbox-poller.md 소관)
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);
    private static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishCompleted(PaymentCompletedEvent event) {
        send(event.groupBuyId(), event);
    }

    public void publishFailed(PaymentFailedEvent event) {
        send(event.groupBuyId(), event);
    }

    // 같은 공동구매의 결제 이벤트가 같은 파티션에 모이도록 groupBuyId를 키로 쓴다 (발행 측과 동일한 기준)
    private void send(Long groupBuyId, Object event) {
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TOPIC, String.valueOf(groupBuyId), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("결제 이벤트 발행 실패 - groupBuyId={}, event={}", groupBuyId, event, ex);
                    }
                });
    }
}
