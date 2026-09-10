package com.wellbuying.domain.payment.event;

import com.wellbuying.domain.payment.entity.PaymentEventOutbox;
import com.wellbuying.domain.payment.repository.PaymentEventOutboxRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 아웃박스 패턴: Kafka에 직접 발행하지 않고 이 트랜잭션 안에서 아웃박스 행만 기록한다.
// 그래야 결제 상태 전이(PAID / PAYMENT_FAILED)와 이벤트 기록이 하나의 DB 트랜잭션으로 원자적으로 묶여,
// Kafka 장애 시에도 발행 유실이 없다(dual write 문제 해소). 실제 발행은 PaymentOutboxRelay/PaymentOutboxDispatcher가
// 별도 트랜잭션에서 폴링하며 수행한다. 따라서 이 클래스의 메서드는 반드시 호출자의 @Transactional 메서드 안에서,
// 상태 전이와 같은 트랜잭션에서 호출돼야 한다(PaymentTransactionService).
// GroupBuyEventPublisher와 같은 방식.
@Component
public class PaymentEventPublisher {

    private final PaymentEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(PaymentEventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishCompleted(PaymentCompletedEvent event) {
        record(event.groupBuyId(), event.eventType(), event);
    }

    public void publishFailed(PaymentFailedEvent event) {
        record(event.groupBuyId(), event.eventType(), event);
    }

    // 같은 공동구매의 결제 이벤트가 같은 파티션에 모이도록 groupBuyId를 나중에 릴레이가 Kafka 메시지 키로 그대로 사용한다.
    // payload는 통째로 저장한다 - settlement/notification이 이벤트 데이터를 그대로 필요로 하고, payment 행이
    // 나중에 바뀌어도 발행 당시 스냅샷이 남아야 하기 때문
    private void record(Long groupBuyId, String eventType, Object event) {
        String payload = objectMapper.writeValueAsString(event);
        outboxRepository.save(PaymentEventOutbox.of(groupBuyId, eventType, payload));
    }
}
