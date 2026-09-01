package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// GroupBuyOutboxRelay가 배치 전체를 병렬로 Kafka에 발행한 결과(성공/실패)를 DB에 반영한다.
// 참여자 수만큼(최대 배치 크기만큼) 건별로 개별 UPDATE를 내보내지 않도록, 성공/실패 각각을
// 한 번의 벌크 UPDATE로 묶어서 처리한다.
@Component
public class GroupBuyOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyOutboxDispatcher.class);

    private final GroupBuyEventOutboxRepository outboxRepository;

    public GroupBuyOutboxDispatcher(GroupBuyEventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // 발행 성공 - published_at을 한 번의 UPDATE로 채워, 다음 릴레이 주기의 미발행 조회 대상에서 한꺼번에 빠지게 한다
    @Transactional
    public void markPublished(List<GroupBuyEventOutbox> events) {
        if (events.isEmpty()) {
            return;
        }
        outboxRepository.markPublished(events.stream().map(GroupBuyEventOutbox::getId).toList(), LocalDateTime.now());
    }

    // 발행 실패 - retryCount를 한 번의 UPDATE로 증가시킨다. published_at은 그대로 null이라 retryCount가
    // MAX_RETRY_COUNT 미만인 동안 다음 릴레이 주기에 재조회되어 재시도된다(at-least-once).
    // 로그 판단(포기 vs 재시도)만 메모리에서 하고(recordFailure/isRetryExhausted), 실제 반영은 벌크 UPDATE 한 번으로 한다
    @Transactional
    public void recordFailures(List<DispatchFailure> failures) {
        if (failures.isEmpty()) {
            return;
        }
        for (DispatchFailure failure : failures) {
            GroupBuyEventOutbox event = failure.event();
            event.recordFailure();
            if (event.isRetryExhausted()) {
                log.error("아웃박스 이벤트 발행 포기(최대 {}회 재시도 초과) - id={}, groupBuyId={}, eventType={}",
                        GroupBuyEventOutbox.MAX_RETRY_COUNT, event.getId(), event.getGroupBuyId(),
                        event.getEventType(), failure.cause());
            } else {
                log.error("아웃박스 이벤트 발행 실패({}번째 재시도) - id={}, groupBuyId={}, eventType={}",
                        event.getRetryCount(), event.getId(), event.getGroupBuyId(), event.getEventType(),
                        failure.cause());
            }
        }
        outboxRepository.incrementRetryCount(failures.stream().map(failure -> failure.event().getId()).toList());
    }

    public record DispatchFailure(GroupBuyEventOutbox event, Throwable cause) {
    }
}
