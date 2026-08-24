package com.wellbuying.groupbuy.service;

import com.wellbuying.groupbuy.domain.GroupBuy;
import com.wellbuying.groupbuy.domain.GroupBuyPart;
import com.wellbuying.groupbuy.domain.GroupBuyPartStatus;
import com.wellbuying.groupbuy.domain.GroupBuyPrice;
import com.wellbuying.groupbuy.domain.GroupBuyStatus;
import com.wellbuying.groupbuy.event.AfterCommitExecutor;
import com.wellbuying.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 시간 기반 상태 전이 담당 - 참여 시점의 재고 소진 판정(즉시 SUCCESS)은 GroupBuyParticipationService에서 처리하고,
// 여기서는 시작 시각/마감 시각 도래에 따른 전이만 다룬다
@Component
public class GroupBuyLifecycleScheduler {

    // 한 번의 실행에서 처리할 최대 건수 - 적체가 쌓여도 메모리 사용량을 예측 가능하게 유지하고,
    // 처리 못한 나머지는 상태가 그대로라 60초 뒤 다음 실행에서 이어서 처리된다
    private static final Limit BATCH_LIMIT = Limit.of(500);

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final GroupBuyEventPublisher groupBuyEventPublisher;

    public GroupBuyLifecycleScheduler(GroupBuyRepository groupBuyRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyPriceRepository groupBuyPriceRepository,
            GroupBuyCounterRepository groupBuyCounterRepository, GroupBuyEventPublisher groupBuyEventPublisher) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.groupBuyEventPublisher = groupBuyEventPublisher;
    }

    // 시작 시각이 지난 READY 공동구매를 ONGOING으로 전환
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void openReadyGroupBuys() {
        LocalDateTime now = LocalDateTime.now();
        groupBuyRepository.findByStatusAndStartAtLessThanEqual(GroupBuyStatus.READY, now, BATCH_LIMIT)
                .forEach(GroupBuy::start);
    }

    // 마감 시각이 지난 ONGOING 공동구매를 최소 수량 달성 여부로 SUCCESS/FAILED 확정하고 이벤트를 발행
    // 대상이 여러 건이어도 확정 참여자 조회(DB)와 카운터 정리(Redis)는 각각 한 번씩만 호출한다 (N+1 방지)
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeOngoingGroupBuys() {
        LocalDateTime now = LocalDateTime.now();
        List<GroupBuy> targets = groupBuyRepository.findByStatusAndEndAtLessThanEqual(GroupBuyStatus.ONGOING, now,
                BATCH_LIMIT);
        if (targets.isEmpty()) {
            return;
        }

        List<Long> succeededIds = targets.stream()
                .filter(GroupBuy::reachedMinQuantity)
                .map(GroupBuy::getId)
                .toList();
        Map<Long, List<GroupBuyPart>> confirmedPartsByGroupBuyId = succeededIds.isEmpty()
                ? Map.of()
                : groupBuyPartRepository.findByGroupBuyIdInAndStatus(succeededIds, GroupBuyPartStatus.CONFIRMED)
                        .stream()
                        .collect(Collectors.groupingBy(GroupBuyPart::getGroupBuyId));
        Map<Long, List<GroupBuyPrice>> priceTiersByGroupBuyId = succeededIds.isEmpty()
                ? Map.of()
                : groupBuyPriceRepository.findByGroupBuyIdIn(succeededIds).stream()
                        .collect(Collectors.groupingBy(GroupBuyPrice::getGroupBuyId));

        for (GroupBuy groupBuy : targets) {
            if (groupBuy.reachedMinQuantity()) {
                groupBuy.succeed();
                List<GroupBuyPart> confirmedParts = confirmedPartsByGroupBuyId.getOrDefault(groupBuy.getId(),
                        List.of());
                // 마감 시점의 최종 누적 수량 기준 단가를 확정 참여자 전원에게 소급 적용해 모두 같은 가격을 내게 한다
                List<GroupBuyPrice> priceTiers = priceTiersByGroupBuyId.getOrDefault(groupBuy.getId(), List.of());
                int finalPrice = GroupBuyPriceCalculator.resolveUnitPrice(priceTiers, groupBuy.getCurrentQuantity());
                confirmedParts.forEach(part -> part.applyFinalPrice(finalPrice));
                AfterCommitExecutor.run(() -> groupBuyEventPublisher.publishCompleted(groupBuy, confirmedParts));
            } else {
                groupBuy.fail();
                AfterCommitExecutor.run(() -> groupBuyEventPublisher.publishFailed(groupBuy));
            }
        }

        groupBuyCounterRepository.deleteAll(targets.stream().map(GroupBuy::getId).toList());
    }
}
