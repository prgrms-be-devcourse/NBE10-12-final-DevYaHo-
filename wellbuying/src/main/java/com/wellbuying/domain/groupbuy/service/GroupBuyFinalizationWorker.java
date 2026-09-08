package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 성사(SUCCESS)는 됐지만 아직 확정 참여자 최종가 반영/outbox 이벤트 기록이 안 된 공동구매를 뒤이어 처리한다.
// GroupBuyParticipationService(매진 즉시 확정)와 GroupBuyCloseProcessor.closeSucceeded(마감 확정) 양쪽 모두
// 상태만 SUCCESS로 바꾸고 끝내므로, 트리거한 참여 요청/스케줄러 틱은 참여자 수(N)와 무관하게 항상 빠르게 끝난다.
// 이 워커가 그 뒤를 이어 N명분 벌크 UPDATE + outbox INSERT를 대신 짊어진다 - outbox 릴레이(3초/200건)와
// 비슷한 리듬으로 돈다
@Component
public class GroupBuyFinalizationWorker {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyFinalizationWorker.class);

    // 한 번의 실행에서 처리할 최대 건수 - GroupBuyOutboxRelay와 동일한 사고방식: 적체가 쌓여도
    // 메모리 사용량을 예측 가능하게 유지하고, 처리 못한 나머지는 finalized_at이 그대로 null이라
    // 다음 실행에서 자연스럽게 이어서 처리된다
    private static final Limit BATCH_LIMIT = Limit.of(200);

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyCloseProcessor groupBuyCloseProcessor;

    public GroupBuyFinalizationWorker(GroupBuyRepository groupBuyRepository,
            GroupBuyPriceRepository groupBuyPriceRepository, GroupBuyCloseProcessor groupBuyCloseProcessor) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
        this.groupBuyCloseProcessor = groupBuyCloseProcessor;
    }

    @Scheduled(fixedDelay = 3_000)
    public void finalizeSucceededGroupBuys() {
        List<GroupBuy> pending = groupBuyRepository
                .findByStatusAndFinalizedAtIsNullOrderByIdAsc(GroupBuyStatus.SUCCESS, BATCH_LIMIT);
        if (pending.isEmpty()) {
            return;
        }

        // 가격 구간 조회는 배치 전체에 대해 한 번만 호출해 N+1을 피한다 (대상 상한은 BATCH_LIMIT,
        // 공동구매당 가격 구간은 몇 개뿐이라 배치 전체를 한 번에 모아도 상한이 작다).
        // 반면 확정 참여자·최종가 반영·이벤트 발행은 건당 수백~수천 명일 수 있어 GroupBuyCloseProcessor
        // 안에서 그 건에 필요한 만큼만, 건별 트랜잭션으로 처리하도록 위임한다
        List<Long> groupBuyIds = pending.stream().map(GroupBuy::getId).toList();
        Map<Long, List<GroupBuyPrice>> priceTiersByGroupBuyId = groupBuyPriceRepository
                .findByGroupBuyIdIn(groupBuyIds).stream()
                .collect(Collectors.groupingBy(GroupBuyPrice::getGroupBuyId));

        for (GroupBuy groupBuy : pending) {
            try {
                groupBuyCloseProcessor.finalizeSucceeded(groupBuy.getId(),
                        priceTiersByGroupBuyId.getOrDefault(groupBuy.getId(), List.of()));
            } catch (Exception e) {
                log.error("공동구매 최종가 반영/이벤트 기록 실패 - groupBuyId: {}", groupBuy.getId(), e);
            }
        }
    }
}
