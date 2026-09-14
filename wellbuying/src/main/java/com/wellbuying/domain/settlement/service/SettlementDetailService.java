package com.wellbuying.domain.settlement.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.settlement.dto.SettlementParticipantResponse;
import com.wellbuying.domain.settlement.dto.SettlementProgressResponse;
import com.wellbuying.domain.settlement.entity.SettlementItem;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 정산 목록 한 건의 상세. PENDING 건은 결제 진행도, COMPLETED 건은 결제한 참여자 명단을 보여준다
// (05-monthly-settlement-list.md 참고 - 목록 카드를 클릭했을 때의 상세 화면).
// groupBuyId가 이 판매자 소유인지는 GroupBuy.producerId로 확인한다 - settlement_item/settlement은
// 아직 없을 수도 있어(=진행도 0/0) groupBuy 존재만으로 접근 가능 여부를 판단한다.
@Service
public class SettlementDetailService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final MemberRepository memberRepository;

    public SettlementDetailService(GroupBuyRepository groupBuyRepository,
            GroupBuyPartRepository groupBuyPartRepository, SettlementItemRepository settlementItemRepository,
            MemberRepository memberRepository) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.memberRepository = memberRepository;
    }

    // PENDING 상세 - 확정 참여자(GroupBuyPartStatus.CONFIRMED) 중 몇 명이 결제까지 끝냈는지
    @Transactional(readOnly = true)
    public SettlementProgressResponse getProgress(Long producerId, Long groupBuyId) {
        requireOwnGroupBuy(producerId, groupBuyId);
        long total = groupBuyPartRepository.countByGroupBuyIdAndStatus(groupBuyId, GroupBuyPartStatus.CONFIRMED);
        long paid = settlementItemRepository.countByGroupBuyId(groupBuyId);
        return new SettlementProgressResponse(total, paid);
    }

    // COMPLETED 상세 - 결제한 참여자 명단(결제 순)
    @Transactional(readOnly = true)
    public List<SettlementParticipantResponse> getParticipants(Long producerId, Long groupBuyId) {
        requireOwnGroupBuy(producerId, groupBuyId);
        List<SettlementItem> items = settlementItemRepository.findByGroupBuyIdOrderByPaidAtAsc(groupBuyId);
        Map<Long, Member> membersById = memberRepository
                .findAllById(items.stream().map(SettlementItem::getMemberId).distinct().toList()).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        return items.stream()
                .map(item -> {
                    Member member = membersById.get(item.getMemberId());
                    return new SettlementParticipantResponse(item.getMemberId(),
                            member != null ? member.getName() : null, item.getAmount(), item.getPaidAt());
                })
                .toList();
    }

    private void requireOwnGroupBuy(Long producerId, Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
        if (!groupBuy.getProducerId().equals(producerId)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FORBIDDEN);
        }
    }
}
