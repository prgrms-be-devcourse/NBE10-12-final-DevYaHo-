package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 공동구매 1건의 마감/최종화 처리를 각각 별도 트랜잭션으로 수행한다 - GroupBuyLifecycleScheduler나
// GroupBuyFinalizationWorker가 배치 전체를 하나의 트랜잭션으로 묶으면 특정 한 건에서 예외가 나도 이번
// 배치의 나머지 전부가 롤백되므로, 건 단위로 격리해 한 건의 실패가 다른 건에 영향을 주지 않도록 한다.
// closeSucceeded/closeFailed: 상태만 확정(빠름, 트리거 경로가 참여자 수와 무관하게 즉시 끝남).
// finalizeSucceeded: 확정 참여자 전원 최종가 반영 + outbox 이벤트 기록(참여자 수에 비례, 무거움) -
// GroupBuyFinalizationWorker가 별도 스케줄 틱에서 호출한다. 이벤트 발행(아웃박스 기록)은 반드시 이 안에서
// 호출해야 최종가 확정과 같은 트랜잭션으로 원자적으로 묶인다.
@Component
public class GroupBuyCloseProcessor {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final GroupBuyEventPublisher groupBuyEventPublisher;

    public GroupBuyCloseProcessor(GroupBuyRepository groupBuyRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyCounterRepository groupBuyCounterRepository,
            GroupBuyEventPublisher groupBuyEventPublisher) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.groupBuyEventPublisher = groupBuyEventPublisher;
    }

    // 최소 수량 달성 - SUCCESS 확정만 하고 즉시 반환한다. 참여자 수(N)에 비례하는 무거운 작업
    // (최종가 벌크 반영 + outbox 기록)은 여기서 하지 않는다 - GroupBuyFinalizationWorker가 finalizeSucceeded로
    // 뒤이어 처리하므로, 이 메서드를 호출한 트리거(스케줄러 틱)는 N과 무관하게 항상 빠르게 끝난다
    @Transactional
    public GroupBuy closeSucceeded(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        groupBuy.succeed();
        groupBuyCounterRepository.delete(groupBuyId);
        return groupBuy;
    }

    // SUCCESS 확정 이후 확정 참여자 전원에게 최종 단가를 반영하고 성사 이벤트를 기록한다 - closeSucceeded와
    // 별도 트랜잭션으로, GroupBuyFinalizationWorker가 자신의 스케줄 틱에서 호출한다. priceTiers는 호출 측이
    // 배치 전체에 대해 한 번만 조회해 건네주므로(N+1 방지) 여기서 다시 조회하지 않는다
    @Transactional
    public void finalizeSucceeded(Long groupBuyId, List<GroupBuyPrice> priceTiers) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        int finalPrice = GroupBuyPriceCalculator.resolveUnitPrice(priceTiers, groupBuy.getCurrentQuantity());
        // markFinalized()는 반드시 아래 applyFinalPriceToConfirmedParts 호출보다 먼저 와야 한다 - 그 호출은
        // flushAutomatically=true라 실행 직전 대기 중인 변경분(지금 markFinalized로 만든 dirty 상태)을 먼저
        // flush해 DB에 반영한 뒤 벌크 UPDATE를 실행하고, 곧바로 clearAutomatically로 영속성 컨텍스트를 비워
        // groupBuy를 detached로 만든다. 순서를 바꾸면(= detached된 뒤에 markFinalized 호출) 이 mutation은
        // dirty checking 대상에서 빠져 DB에 영영 반영되지 않고, 워커가 이 건을 매번 다시 집어 이벤트를
        // 중복 발행하게 된다 (실측: finalized_at이 계속 null로 남아 outbox가 매 틱 300건씩 계속 쌓임)
        groupBuy.markFinalized();
        // 확정 참여자 전원에게 최종 단가를 벌크 UPDATE 한 문장으로 반영한다 - 엔티티를 조회해 하나씩
        // applyFinalPrice()로 mutate하면 참여자 수(N)만큼 dirty checking UPDATE가 나가므로 피한다
        groupBuyPartRepository.applyFinalPriceToConfirmedParts(groupBuyId, finalPrice, GroupBuyPartStatus.CONFIRMED);
        // Kafka 이벤트 발행용 확정 참여자 목록 - 이 건(공동구매 1개)에 대해서만 조회하므로, 이번 워커
        // 배치에서 여러 건이 동시에 성사돼도 한 번에 메모리에 올라가는 양이 이 건의 참여자 수로 한정된다.
        // 위 벌크 UPDATE가 이미 반영된 뒤 재조회하는 것이라 최종가가 그대로 채워져 있다 - 여기서 다시
        // forEach로 mutate하면 방금 피한 dirty checking UPDATE가 그대로 재발하므로 절대 건드리지 않는다
        List<GroupBuyPart> confirmedParts = groupBuyPartRepository.findByGroupBuyIdAndStatus(groupBuyId,
                GroupBuyPartStatus.CONFIRMED);
        groupBuyEventPublisher.publishCompleted(groupBuy, confirmedParts);
    }

    // 마감까지 목표 미달 - FAILED 확정
    @Transactional
    public GroupBuy closeFailed(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        groupBuy.fail();
        groupBuyCounterRepository.delete(groupBuyId);
        groupBuyEventPublisher.publishFailed(groupBuy);
        return groupBuy;
    }
}
