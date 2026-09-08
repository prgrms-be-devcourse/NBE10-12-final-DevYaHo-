package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

    // 한 라운드에서 조회할 최대 건수 - 적체가 쌓여도 메모리 사용량을 예측 가능하게 유지한다
    // (처리 못한 나머지는 상태가 그대로라 다음 라운드/틱에서 자연스럽게 이어서 처리된다)
    private static final Limit BATCH_LIMIT = Limit.of(500);

    // 한 틱(60초) 안에서 최대 몇 라운드까지 이어서 마감 처리할지 - GroupBuyOutboxRelay와 동일한 이유
    // (백로그가 있는 동안은 다음 틱을 기다리지 않고 곧바로 다음 라운드를 돈다)
    private static final int MAX_ROUNDS_PER_TICK = 10;

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyCloseProcessor groupBuyCloseProcessor;
    private final Executor groupBuyLifecycleExecutor;

    public GroupBuyLifecycleScheduler(GroupBuyRepository groupBuyRepository,
            GroupBuyCloseProcessor groupBuyCloseProcessor,
            @Qualifier("groupBuyLifecycleExecutor") Executor groupBuyLifecycleExecutor) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyCloseProcessor = groupBuyCloseProcessor;
        this.groupBuyLifecycleExecutor = groupBuyLifecycleExecutor;
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
    // 라운드가 BATCH_LIMIT만큼 꽉 찼으면(아직 더 있을 가능성) 다음 60초 틱을 기다리지 않고 이 틱 안에서 곧바로
    // 다음 라운드를 이어서 처리한다 - GroupBuyOutboxRelay와 동일한 이유(고정 500건/60초가 아니라 밀렸을 때만
    // 쉬지 않고 처리량을 늘리는 구조). 백로그가 없으면 지금까지와 동일하게 라운드 1번만 돌고 끝난다
    @Scheduled(fixedDelay = 60_000)
    public void closeOngoingGroupBuys() {
        for (int round = 0; round < MAX_ROUNDS_PER_TICK; round++) {
            int processed = closeOnce();
            if (processed < BATCH_LIMIT.max()) {
                return;
            }
        }
    }

    // 대상 조회는 라운드 전체에 대해 한 번만 호출해 N+1을 피한다(참여자 수와 무관하게 상한은 BATCH_LIMIT).
    // 실제 상태 확정(쓰기)은 건별로 GroupBuyCloseProcessor에 위임해 트랜잭션을 분리하고, groupBuyLifecycleExecutor로
    // 병렬 실행한다 - 순차로 돌리면 건당 DB 왕복 시간이 그대로 배치 전체에 곱해지기 때문. 트랜잭션이 건별로
    // 분리돼 있어 특정 한 건에서 예외가 나도 나머지 건들의 마감 처리에 영향을 주지 않는다.
    // (성사 확정만 하고 끝난다 - 참여자 최종가 반영/outbox 기록은 GroupBuyFinalizationWorker가 뒤이어 처리)
    private int closeOnce() {
        LocalDateTime now = LocalDateTime.now();
        List<GroupBuy> targets = groupBuyRepository
                .findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(GroupBuyStatus.ONGOING, now, BATCH_LIMIT);
        if (targets.isEmpty()) {
            return 0;
        }

        List<CompletableFuture<Void>> futures = targets.stream()
                .map(groupBuy -> CompletableFuture.runAsync(() -> closeOneSafely(groupBuy), groupBuyLifecycleExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return targets.size();
    }

    private void closeOneSafely(GroupBuy groupBuy) {
        try {
            closeOne(groupBuy);
        } catch (Exception e) {
            log.error("공동구매 마감 처리 실패 - groupBuyId: {}", groupBuy.getId(), e);
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
