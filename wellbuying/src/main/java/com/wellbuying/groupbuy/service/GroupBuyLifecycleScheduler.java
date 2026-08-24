package com.wellbuying.groupbuy.service;

import com.wellbuying.groupbuy.domain.GroupBuy;
import com.wellbuying.groupbuy.domain.GroupBuyPart;
import com.wellbuying.groupbuy.domain.GroupBuyPartStatus;
import com.wellbuying.groupbuy.domain.GroupBuyPrice;
import com.wellbuying.groupbuy.domain.GroupBuyStatus;
import com.wellbuying.groupbuy.event.AfterCommitExecutor;
import com.wellbuying.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 시간 기반 상태 전이 담당 - 참여 시점의 재고 소진 판정(즉시 SUCCESS)은 GroupBuyParticipationService에서 처리하고,
// 여기서는 시작 시각/마감 시각 도래에 따른 전이만 다룬다
@Component
public class GroupBuyLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyLifecycleScheduler.class);

    // 한 번의 실행에서 처리할 최대 건수 - 적체가 쌓여도 메모리 사용량을 예측 가능하게 유지하고,
    // 처리 못한 나머지는 상태가 그대로라 60초 뒤 다음 실행에서 이어서 처리된다
    private static final Limit BATCH_LIMIT = Limit.of(500);

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyCloseProcessor groupBuyCloseProcessor;
    private final GroupBuyEventPublisher groupBuyEventPublisher;

    public GroupBuyLifecycleScheduler(GroupBuyRepository groupBuyRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyPriceRepository groupBuyPriceRepository,
            GroupBuyCloseProcessor groupBuyCloseProcessor, GroupBuyEventPublisher groupBuyEventPublisher) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
        this.groupBuyCloseProcessor = groupBuyCloseProcessor;
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
    // 대상 조회, 확정 참여자 조회, 가격 구간 조회는 배치 전체에 대해 각각 한 번씩만 호출해 N+1을 피하되(읽기 전용),
    // 실제 상태 확정(쓰기)은 건별로 GroupBuyCloseProcessor에 위임해 트랜잭션을 분리한다 - 이렇게 하면 특정 한 건에서
    // 예외가 나도 나머지 건들의 마감 처리가 함께 롤백되지 않고, 다음 실행을 기다리지 않고 계속 진행된다
    @Scheduled(fixedDelay = 60_000)
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
            try {
                closeOne(groupBuy, confirmedPartsByGroupBuyId, priceTiersByGroupBuyId);
            } catch (Exception e) {
                log.error("공동구매 마감 처리 실패 - groupBuyId: {}", groupBuy.getId(), e);
            }
        }
    }

    private void closeOne(GroupBuy groupBuy, Map<Long, List<GroupBuyPart>> confirmedPartsByGroupBuyId,
            Map<Long, List<GroupBuyPrice>> priceTiersByGroupBuyId) {
        if (groupBuy.reachedMinQuantity()) {
            List<GroupBuyPart> confirmedParts = confirmedPartsByGroupBuyId.getOrDefault(groupBuy.getId(), List.of());
            // 마감 시점의 최종 누적 수량 기준 단가를 확정 참여자 전원에게 소급 적용해 모두 같은 가격을 내게 한다.
            // 실제 저장은 GroupBuyCloseProcessor가 UPDATE 한 문장으로 반영하고, 여기서는 이벤트 페이로드 구성을
            // 위해 이미 읽어둔(batch 조회된) 참여자 객체에 계산된 값만 채워 넣는다 (추가 조회 없음)
            List<GroupBuyPrice> priceTiers = priceTiersByGroupBuyId.getOrDefault(groupBuy.getId(), List.of());
            int finalPrice = GroupBuyPriceCalculator.resolveUnitPrice(priceTiers, groupBuy.getCurrentQuantity());
            confirmedParts.forEach(part -> part.applyFinalPrice(finalPrice));
            GroupBuy closedGroupBuy = groupBuyCloseProcessor.closeSucceeded(groupBuy.getId(), finalPrice);
            AfterCommitExecutor.run(() -> groupBuyEventPublisher.publishCompleted(closedGroupBuy, confirmedParts));
        } else {
            GroupBuy closedGroupBuy = groupBuyCloseProcessor.closeFailed(groupBuy.getId());
            AfterCommitExecutor.run(() -> groupBuyEventPublisher.publishFailed(closedGroupBuy));
        }
    }
}
