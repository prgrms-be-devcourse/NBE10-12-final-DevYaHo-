package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
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
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyCloseProcessor groupBuyCloseProcessor;

    public GroupBuyLifecycleScheduler(GroupBuyRepository groupBuyRepository,
            GroupBuyPriceRepository groupBuyPriceRepository, GroupBuyCloseProcessor groupBuyCloseProcessor) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
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

    // 마감 시각이 지난 ONGOING 공동구매를 최소 수량 달성 여부로 SUCCESS/FAILED 확정하고 이벤트를 발행
    // 대상 조회와 가격 구간 조회는 배치 전체에 대해 각각 한 번씩만 호출해 N+1을 피한다(둘 다 참여자 수와
    // 무관하게 상한이 작다: 대상은 BATCH_LIMIT, 가격 구간은 공동구매당 몇 개뿐). 반면 확정 참여자는 공동구매당
    // 수백~수천 명일 수 있어 배치 전체를 한 번에 조회하면 상한이 없어지므로, 건별로 GroupBuyCloseProcessor
    // 안에서 그 건에 필요한 만큼만 조회하도록 위임한다. 실제 상태 확정(쓰기)도 건별로 위임해 트랜잭션을
    // 분리한다 - 이렇게 하면 특정 한 건에서 예외가 나도 나머지 건들의 마감 처리가 함께 롤백되지 않고,
    // 다음 실행을 기다리지 않고 계속 진행된다
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
        Map<Long, List<GroupBuyPrice>> priceTiersByGroupBuyId = succeededIds.isEmpty()
                ? Map.of()
                : groupBuyPriceRepository.findByGroupBuyIdIn(succeededIds).stream()
                        .collect(Collectors.groupingBy(GroupBuyPrice::getGroupBuyId));

        for (GroupBuy groupBuy : targets) {
            try {
                closeOne(groupBuy, priceTiersByGroupBuyId);
            } catch (Exception e) {
                log.error("공동구매 마감 처리 실패 - groupBuyId: {}", groupBuy.getId(), e);
            }
        }
    }

    private void closeOne(GroupBuy groupBuy, Map<Long, List<GroupBuyPrice>> priceTiersByGroupBuyId) {
        if (groupBuy.reachedMinQuantity()) {
            // 마감 시점의 최종 누적 수량 기준 단가를 확정 참여자 전원에게 소급 적용해 모두 같은 가격을 내게 한다.
            // 확정 참여자 조회·최종가 반영·이벤트 발행은 GroupBuyCloseProcessor가 건별 트랜잭션 안에서 처리한다
            List<GroupBuyPrice> priceTiers = priceTiersByGroupBuyId.getOrDefault(groupBuy.getId(), List.of());
            int finalPrice = GroupBuyPriceCalculator.resolveUnitPrice(priceTiers, groupBuy.getCurrentQuantity());
            groupBuyCloseProcessor.closeSucceeded(groupBuy.getId(), finalPrice);
        } else {
            groupBuyCloseProcessor.closeFailed(groupBuy.getId());
        }
    }
}
