package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 공동구매 1건의 마감 확정을 별도 트랜잭션으로 처리한다 - GroupBuyLifecycleScheduler가 배치 전체를
// 하나의 트랜잭션으로 묶으면 특정 한 건에서 예외가 나도 이번 배치의 나머지 전부가 롤백되므로,
// 건 단위로 격리해 한 건의 실패가 다른 건의 마감 처리에 영향을 주지 않도록 한다.
// 이벤트 발행(아웃박스 기록)도 반드시 이 안에서 호출해야 상태 확정과 같은 트랜잭션으로 원자적으로 묶인다.
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

    // 최소 수량 달성 - SUCCESS 확정 + 확정 참여자 전원에게 최종 단가를 UPDATE 한 문장으로 반영
    @Transactional
    public GroupBuy closeSucceeded(Long groupBuyId, int finalPrice) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        groupBuy.succeed();
        // 확정 참여자 전원에게 최종 단가를 벌크 UPDATE 한 문장으로 반영한다 - 엔티티를 조회해 하나씩
        // applyFinalPrice()로 mutate하면 참여자 수(N)만큼 dirty checking UPDATE가 나가므로 피한다.
        // clearAutomatically라 실행 직후 영속성 컨텍스트가 비워진다 (GroupBuyParticipationService의 매진 즉시 확정 경로와 동일한 패턴)
        groupBuyPartRepository.applyFinalPriceToConfirmedParts(groupBuyId, finalPrice, GroupBuyPartStatus.CONFIRMED);
        // Kafka 이벤트 발행용 확정 참여자 목록 - 이 건(공동구매 1개)에 대해서만 조회하므로, 이번 스케줄러
        // 배치에서 여러 건이 동시에 성사돼도 한 번에 메모리에 올라가는 양이 이 건의 참여자 수로 한정된다.
        // 위 벌크 UPDATE가 이미 반영된 뒤 재조회하는 것이라 최종가가 그대로 채워져 있다 - 여기서 다시
        // forEach로 mutate하면 방금 피한 dirty checking UPDATE가 그대로 재발하므로 절대 건드리지 않는다
        List<GroupBuyPart> confirmedParts = groupBuyPartRepository.findByGroupBuyIdAndStatus(groupBuyId,
                GroupBuyPartStatus.CONFIRMED);
        groupBuyCounterRepository.delete(groupBuyId);
        groupBuyEventPublisher.publishCompleted(groupBuy, confirmedParts);
        return groupBuy;
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
