package com.wellbuying.domain.settlement.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import com.wellbuying.domain.settlement.repository.SettlementRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 확정된 정산 내역을 조회한다 (판매자: 자기 것 / 관리자: 전체). 확정 결과는 settlement 테이블이 원본이라
// 이벤트 발행 없이 직접 읽는다. 표시용 공동구매 제목/판매자 이름은 조회 시점에 배치로 조합한다
// (OrderQueryService와 같은 방식 - settlement에 스냅샷으로 복사하지 않는다).
@Service
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final MemberRepository memberRepository;

    public SettlementQueryService(SettlementRepository settlementRepository, GroupBuyRepository groupBuyRepository,
            MemberRepository memberRepository) {
        this.settlementRepository = settlementRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.memberRepository = memberRepository;
    }

    // 판매자 본인의 정산 내역. producerId == 판매자 memberId이므로, 판매자가 아닌 회원은 빈 목록을 받는다
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getMySettlements(Long producerId, Pageable pageable) {
        return toResponsePage(settlementRepository.findByProducerId(producerId, sortedByConfirmedAtDesc(pageable)));
    }

    // 관리자 전체 정산 내역. status가 null이면 전체
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getAllSettlements(SettlementStatus status, Pageable pageable) {
        Pageable sorted = sortedByConfirmedAtDesc(pageable);
        Page<Settlement> settlements = status == null
                ? settlementRepository.findAll(sorted)
                : settlementRepository.findByStatus(status, sorted);
        return toResponsePage(settlements);
    }

    // 클라이언트가 넘긴 sort를 그대로 쓰면 리포지토리 정렬과 겹쳐 꼬일 수 있어(OrderQueryService 참고),
    // 페이지 번호/크기만 취하고 정렬은 최신 확정순으로 강제한다
    private Pageable sortedByConfirmedAtDesc(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "confirmedAt", "id"));
    }

    private Page<SettlementResponse> toResponsePage(Page<Settlement> settlements) {
        List<Settlement> content = settlements.getContent();

        List<Long> groupBuyIds = content.stream().map(Settlement::getGroupBuyId).distinct().toList();
        Map<Long, GroupBuy> groupBuysById = groupBuyRepository.findAllById(groupBuyIds).stream()
                .collect(Collectors.toMap(GroupBuy::getId, Function.identity()));

        List<Long> producerIds = content.stream().map(Settlement::getProducerId).distinct().toList();
        Map<Long, Member> membersById = memberRepository.findAllById(producerIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));

        return settlements.map(settlement -> {
            GroupBuy groupBuy = groupBuysById.get(settlement.getGroupBuyId());
            Member producer = membersById.get(settlement.getProducerId());
            return SettlementResponse.of(settlement,
                    groupBuy != null ? groupBuy.getTitle() : null,
                    producer != null ? producer.getName() : null);
        });
    }
}
