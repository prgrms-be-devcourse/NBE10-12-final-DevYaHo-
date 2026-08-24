package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.domain.GroupBuy;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 공동구매 1건의 마감 확정을 별도 트랜잭션으로 처리한다 - GroupBuyLifecycleScheduler가 배치 전체를
// 하나의 트랜잭션으로 묶으면 특정 한 건에서 예외가 나도 이번 배치의 나머지 전부가 롤백되므로,
// 건 단위로 격리해 한 건의 실패가 다른 건의 마감 처리에 영향을 주지 않도록 한다
@Component
public class GroupBuyCloseProcessor {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;

    public GroupBuyCloseProcessor(GroupBuyRepository groupBuyRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyCounterRepository groupBuyCounterRepository) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
    }

    // 최소 수량 달성 - SUCCESS 확정 + 확정 참여자 전원에게 최종 단가를 UPDATE 한 문장으로 반영 (finalPrice는
    // 호출 측이 배치 조회로 미리 구해둔 값을 그대로 넘겨받으므로 여기서 추가 조회가 없다)
    @Transactional
    public GroupBuy closeSucceeded(Long groupBuyId, int finalPrice) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        groupBuy.succeed();
        groupBuyPartRepository.applyFinalPriceToConfirmedParts(groupBuyId, finalPrice, GroupBuyPartStatus.CONFIRMED);
        groupBuyCounterRepository.delete(groupBuyId);
        return groupBuy;
    }

    // 마감까지 목표 미달 - FAILED 확정
    @Transactional
    public GroupBuy closeFailed(Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        groupBuy.fail();
        groupBuyCounterRepository.delete(groupBuyId);
        return groupBuy;
    }
}
