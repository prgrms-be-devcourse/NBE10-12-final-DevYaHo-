package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 아웃박스 이벤트 1건의 Kafka 발행을 별도 트랜잭션으로 처리한다 - GroupBuyCloseProcessor와 동일한 이유로,
// GroupBuyOutboxRelay가 배치 전체를 하나의 트랜잭션/메서드로 묶으면 한 건의 발행 실패(또는 지연)가
// 나머지 건의 재시도까지 함께 막으므로, 건 단위로 격리해 한 건의 실패가 다른 건에 영향을 주지 않게 한다
@Component
public class GroupBuyOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyOutboxDispatcher.class);
    private static final String TOPIC = "groupbuy-events";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final GroupBuyEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public GroupBuyOutboxDispatcher(GroupBuyEventOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    // 발행 성공 시에만 publishedAt을 채워 커밋한다 - 실패하면 아무것도 반영되지 않으므로 다음 릴레이 주기에
    // 같은 행이 다시 조회되어 재시도된다(at-least-once). 컨슈머 쪽에서 이벤트 단위 멱등 처리를 전제로 한다
    @Transactional
    public void dispatch(GroupBuyEventOutbox event) {
        try {
            kafkaTemplate.send(TOPIC, String.valueOf(event.getGroupBuyId()), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.markPublished();
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("아웃박스 이벤트 발행 실패 - id={}, groupBuyId={}, eventType={}", event.getId(), event.getGroupBuyId(),
                    event.getEventType(), e);
        }
    }
}
