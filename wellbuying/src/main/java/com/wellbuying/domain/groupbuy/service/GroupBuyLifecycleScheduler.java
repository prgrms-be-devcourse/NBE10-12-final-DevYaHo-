package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 시간 기반 상태 전이 담당 - 참여 시점의 재고 소진 판정(즉시 SUCCESS)은 GroupBuyParticipationService에서 처리하고,
// 여기서는 시작 시각/마감 시각 도래에 따른 전이만 다룬다. 성사(SUCCESS) 확정 시 참여자 최종가 반영/outbox 기록은
// 이 스케줄러가 하지 않는다 - GroupBuyFinalizationWorker가 별도로 비동기 처리한다 (closeOne 참고)
@Component
public class GroupBuyLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyLifecycleScheduler.class);

    // 한 번의 실행에서 처리할 최대 건수 - 적체가 쌓여도 메모리 사용량을 예측 가능하게 유지하고,
    // 처리 못한 나머지는 상태가 그대로라 60초 뒤 다음 실행에서 이어서 처리된다
    private static final Limit BATCH_LIMIT = Limit.of(500);

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyCloseProcessor groupBuyCloseProcessor;

    public GroupBuyLifecycleScheduler(GroupBuyRepository groupBuyRepository,
            GroupBuyCloseProcessor groupBuyCloseProcessor) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyCloseProcessor = groupBuyCloseProcessor;
    }

    // 시작 시각이 지난 READY 공동구매를 ONGOING으로 전환
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void openReadyGroupBuys() {
        LocalDateTime now = LocalDateTime.now();
        groupBuyRepository.findByStatusAndStartAtLessThanEqual(GroupBuyStatus.READY, now, BATCH_LIMIT)
                .forEach(GroupBuy::start);
    }

    // 마감 시각이 지난 ONGOING 공동구매를 최소 수량 달성 여부로 SUCCESS/FAILED 확정한다.
    // 대상 조회는 배치 전체에 대해 한 번만 호출해 N+1을 피한다(참여자 수와 무관하게 상한은 BATCH_LIMIT).
    // 실제 상태 확정(쓰기)은 건별로 GroupBuyCloseProcessor에 위임해 트랜잭션을 분리한다 - 이렇게 하면 특정
    // 한 건에서 예외가 나도 나머지 건들의 마감 처리가 함께 롤백되지 않고, 다음 실행을 기다리지 않고 계속 진행된다.
    // (성사 확정만 하고 끝난다 - 참여자 최종가 반영/outbox 기록은 GroupBuyFinalizationWorker가 뒤이어 처리)
    @Scheduled(fixedDelay = 60_000)
    public void closeOngoingGroupBuys() {
        LocalDateTime now = LocalDateTime.now();
        List<GroupBuy> targets = groupBuyRepository
                .findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(GroupBuyStatus.ONGOING, now, BATCH_LIMIT);
        if (targets.isEmpty()) {
            return;
        }

        for (GroupBuy groupBuy : targets) {
            try {
                closeOne(groupBuy);
            } catch (Exception e) {
                log.error("공동구매 마감 처리 실패 - groupBuyId: {}", groupBuy.getId(), e);
            }
        }
    }

    private void closeOne(GroupBuy groupBuy) {
        if (groupBuy.reachedMinQuantity()) {
            groupBuyCloseProcessor.closeSucceeded(groupBuy.getId());
        } else {
            groupBuyCloseProcessor.closeFailed(groupBuy.getId());
        }
    }
}
