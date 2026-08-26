package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 아웃박스에 쌓인 미발행 이벤트를 주기적으로 폴링해 Kafka로 발행한다.
// 상태 변경(성사/실패/취소 확정)과 아웃박스 기록은 GroupBuyEventPublisher가 이미 같은 트랜잭션으로 묶어뒀으므로,
// 여기서 Kafka 발행이 잠시 실패하거나 이 프로세스가 죽어도 이벤트 자체는 DB에 남아 다음 주기에 재시도된다
@Component
public class GroupBuyOutboxRelay {

    // 한 번의 실행에서 처리할 최대 건수 - GroupBuyLifecycleScheduler의 BATCH_LIMIT과 동일한 이유(예측 가능한 메모리 사용,
    // 처리 못한 나머지는 published_at이 그대로 null이라 다음 실행에서 자연스럽게 이어서 처리됨)
    private static final Limit BATCH_LIMIT = Limit.of(200);

    private final GroupBuyEventOutboxRepository outboxRepository;
    private final GroupBuyOutboxDispatcher dispatcher;

    public GroupBuyOutboxRelay(GroupBuyEventOutboxRepository outboxRepository, GroupBuyOutboxDispatcher dispatcher) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 3_000)
    public void relay() {
        List<GroupBuyEventOutbox> pending = outboxRepository.findByPublishedAtIsNullOrderByIdAsc(BATCH_LIMIT);
        pending.forEach(dispatcher::dispatch);
    }
}
