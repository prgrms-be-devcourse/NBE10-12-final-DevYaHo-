package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 아웃박스 패턴: Kafka에 직접 발행하지 않고 이 트랜잭션 안에서 아웃박스 행만 기록한다.
// 그래야 상태 변경(성사/실패/취소)과 이벤트 기록이 하나의 DB 트랜잭션으로 원자적으로 묶여, 발행 유실이 없다.
// 실제 Kafka 발행은 GroupBuyOutboxRelay/GroupBuyOutboxDispatcher가 별도 트랜잭션에서 폴링하며 수행한다.
// 그래서 이 클래스의 메서드는 반드시 호출자의 @Transactional 메서드 안에서, 상태 변경과 같은 트랜잭션에서 호출돼야 한다.
@Component
public class GroupBuyEventPublisher {

    private final GroupBuyEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public GroupBuyEventPublisher(GroupBuyEventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    // 공동구매 성사 - 확정된 참여자 각각에 대해 이벤트를 개별 기록 (결제 도메인이 참여자 단위로 후속 처리를 하도록).
    // 참여자 수만큼 save()를 개별 호출하면 그만큼 개별 INSERT 왕복이 발생하므로 saveAll()로 한 번에 묶는다
    public void publishCompleted(GroupBuy groupBuy, List<GroupBuyPart> confirmedParts) {
        List<GroupBuyEventOutbox> events = confirmedParts.stream()
                .map(part -> toOutbox(groupBuy.getId(), GroupBuyEventType.GROUP_BUY_COMPLETED.code(),
                        GroupBuyCompletedEvent.of(groupBuy, part)))
                .toList();
        outboxRepository.saveAll(events);
    }

    public void publishFailed(GroupBuy groupBuy) {
        record(groupBuy.getId(), GroupBuyEventType.GROUP_BUY_FAILED.code(), GroupBuyFailedEvent.of(groupBuy));
    }

    public void publishCanceled(GroupBuy groupBuy) {
        record(groupBuy.getId(), GroupBuyEventType.GROUP_BUY_CANCELED.code(), GroupBuyCanceledEvent.of(groupBuy));
    }

    private void record(Long groupBuyId, String eventType, Object event) {
        outboxRepository.save(toOutbox(groupBuyId, eventType, event));
    }

    // 같은 공동구매의 이벤트가 같은 파티션으로 모이도록 groupBuyId를 나중에 릴레이가 Kafka 메시지 키로 그대로 사용한다
    private GroupBuyEventOutbox toOutbox(Long groupBuyId, String eventType, Object event) {
        String payload = objectMapper.writeValueAsString(event);
        return GroupBuyEventOutbox.of(groupBuyId, eventType, payload);
    }
}
