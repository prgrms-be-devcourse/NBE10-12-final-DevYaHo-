package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// groupbuy-events 토픽 구독.
// 이 토픽에는 성사/실패/취소가 모두 흐르므로 eventType으로 성사 건만 골라낸다
// (실패/취소는 결제 도메인이 할 일이 없다 - 아직 결제가 시작되지 않았기 때문)
// 알림 도메인에도 groupbuy-events를 듣는 GroupBuyEventConsumer가 있어 빈 이름이 겹친다 - 도메인 접두사로 구분한다
@Component
public class PaymentGroupBuyEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentGroupBuyEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PaymentProcessor paymentProcessor;

    public PaymentGroupBuyEventConsumer(ObjectMapper objectMapper, PaymentProcessor paymentProcessor) {
        this.objectMapper = objectMapper;
        this.paymentProcessor = paymentProcessor;
    }

    @KafkaListener(topics = "groupbuy-events", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        GroupBuyCompletedMessage message;
        try {
            message = objectMapper.readValue(payload, GroupBuyCompletedMessage.class);
        } catch (RuntimeException e) {
            // 파싱조차 안 되는 메시지는 재시도해도 똑같이 실패하므로 로그만 남기고 넘어간다 (DLT는 04-failure-retry.md 소관)
            log.error("성사 이벤트 역직렬화 실패 - payload={}", payload, e);
            return;
        }

        if (!GroupBuyCompletedMessage.TYPE.equals(message.eventType())) {
            return;
        }
        // 성사 시 결제 금액이 반드시 채워지지만, null이면 금액을 계산할 수 없어 진행하지 않는다
        if (message.appliedPrice() == null) {
            log.error("발행된 메시지에 결제 가격정보가 없음 - partId={}", message.partId());
            return;
        }

        paymentProcessor.process(message);
    }
}
