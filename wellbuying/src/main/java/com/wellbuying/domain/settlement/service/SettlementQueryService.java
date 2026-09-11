package com.wellbuying.domain.settlement.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.settlement.dto.SettlementListItemResponse;
import com.wellbuying.domain.settlement.dto.SettlementListStatus;
import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import com.wellbuying.domain.settlement.repository.SettlementPendingRow;
import com.wellbuying.domain.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 확정된 정산 내역을 조회한다.
// - 판매자(getMySettlements): 월별(공동구매 finalizedAt이 속한 달 기준) + 대기중(PENDING)/완료(COMPLETED)
//   필터. finalizedAt으로 귀속시키는 이유: settlement.confirmedAt으로 귀속시키면, 월말에 성사된 건이
//   다음 달에 확정될 때 "대기중 리스트에서 사라졌다가 완료 리스트의 다른 달에 나타나는" 것처럼 보인다.
//   finalizedAt은 확정 여부와 무관하게 고정이라 이 문제가 없다 (03-query-api.md → 05 참고).
// - 관리자(getAllSettlements): 전체, 기존 그대로 (SettlementResponse/SettlementStatus 사용, 변경 없음)
// 표시용 공동구매 제목/판매자 이름은 조회 시점에 배치로 조합한다 (OrderQueryService와 같은 방식).
@Service
public class SettlementQueryService {

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final MemberRepository memberRepository;
    private final BigDecimal platformFeeRate;

    public SettlementQueryService(SettlementRepository settlementRepository,
            SettlementItemRepository settlementItemRepository, GroupBuyRepository groupBuyRepository,
            MemberRepository memberRepository,
            @Value("${settlement.platform-fee-rate:0.05}") BigDecimal platformFeeRate) {
        this.settlementRepository = settlementRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.memberRepository = memberRepository;
        this.platformFeeRate = platformFeeRate;
    }

    // year/month 중 하나라도 없으면 이번 달 기준. status 생략 시 대기중+완료 전체를 합쳐서 보여준다
    @Transactional(readOnly = true)
    public Page<SettlementListItemResponse> getMySettlements(Long producerId, Integer year, Integer month,
            SettlementListStatus status, Pageable pageable) {
        YearMonth targetMonth = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();
        LocalDateTime from = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime to = targetMonth.plusMonths(1).atDay(1).atStartOfDay();

        if (status == SettlementListStatus.PENDING) {
            return toPendingPage(settlementItemRepository.findPendingByProducerIdAndFinalizedAtRange(
                    producerId, from, to, unsortedPage(pageable)));
        }
        if (status == SettlementListStatus.COMPLETED) {
            return toCompletedPage(settlementRepository.findByProducerIdAndGroupBuyFinalizedAtRange(
                    producerId, from, to, completedSortedPage(pageable)));
        }

        // 전체 - 두 출처를 그 달 전체 범위로 통째로 가져와 합친 뒤 메모리에서 페이지를 자른다.
        // 한 판매자의 한 달치 공동구매 정산 건수는 실무적으로 작아(그룹바이 단위) 안전하다
        List<SettlementListItemResponse> pending = toPendingResponses(settlementItemRepository
                .findPendingByProducerIdAndFinalizedAtRange(producerId, from, to, Pageable.unpaged())
                .getContent());
        List<SettlementListItemResponse> completed = toCompletedResponses(settlementRepository
                .findByProducerIdAndGroupBuyFinalizedAtRange(producerId, from, to, Pageable.unpaged())
                .getContent());
        List<SettlementListItemResponse> merged = new ArrayList<>(pending.size() + completed.size());
        merged.addAll(pending);
        merged.addAll(completed);
        merged.sort(Comparator.comparing(SettlementListItemResponse::finalizedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return paginate(merged, pageable);
    }

    // 관리자 전체 정산 내역. status가 null이면 전체 (기존 그대로 - 판매자 목록과 무관)
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getAllSettlements(SettlementStatus status, Pageable pageable) {
        Pageable sorted = sortedByConfirmedAtDesc(pageable);
        Page<Settlement> settlements = status == null
                ? settlementRepository.findAll(sorted)
                : settlementRepository.findByStatus(status, sorted);
        return toResponsePage(settlements);
    }

    // 클라이언트가 넘긴 sort를 그대로 쓰면 리포지토리 정렬과 겹쳐 꼬일 수 있어(OrderQueryService 참고),
    // 페이지 번호/크기만 취하고 정렬은 여기서 고정한다
    private Pageable sortedByConfirmedAtDesc(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "confirmedAt", "id"));
    }

    private Pageable completedSortedPage(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "confirmedAt"));
    }

    // 네이티브 쿼리는 자체 ORDER BY를 쓰므로 Sort를 얹지 않는다(얹어도 네이티브 SQL에 반영되지 않는다)
    private Pageable unsortedPage(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private Page<SettlementResponse> toResponsePage(Page<Settlement> settlements) {
        List<Settlement> content = settlements.getContent();
        Map<Long, GroupBuy> groupBuysById = loadGroupBuys(content.stream().map(Settlement::getGroupBuyId).toList());
        Map<Long, Member> membersById = loadMembers(content.stream().map(Settlement::getProducerId).toList());
        return settlements.map(settlement -> {
            GroupBuy groupBuy = groupBuysById.get(settlement.getGroupBuyId());
            Member producer = membersById.get(settlement.getProducerId());
            return SettlementResponse.of(settlement,
                    groupBuy != null ? groupBuy.getTitle() : null,
                    producer != null ? producer.getName() : null);
        });
    }

    private Page<SettlementListItemResponse> toPendingPage(Page<SettlementPendingRow> rows) {
        Map<Long, Member> membersById = loadMembers(
                rows.getContent().stream().map(SettlementPendingRow::getProducerId).toList());
        Map<Long, GroupBuy> groupBuysById = loadGroupBuys(
                rows.getContent().stream().map(SettlementPendingRow::getGroupBuyId).toList());
        return rows.map(row -> toPendingResponse(row, membersById.get(row.getProducerId()),
                groupBuysById.get(row.getGroupBuyId())));
    }

    private List<SettlementListItemResponse> toPendingResponses(List<SettlementPendingRow> rows) {
        Map<Long, Member> membersById = loadMembers(rows.stream().map(SettlementPendingRow::getProducerId).toList());
        Map<Long, GroupBuy> groupBuysById =
                loadGroupBuys(rows.stream().map(SettlementPendingRow::getGroupBuyId).toList());
        return rows.stream()
                .map(row -> toPendingResponse(row, membersById.get(row.getProducerId()),
                        groupBuysById.get(row.getGroupBuyId())))
                .toList();
    }

    // platformFee/payout은 확정 전 예상치 - SettlementConfirmationService와 같은 식(floor(총매출 x 수수료율))
    private SettlementListItemResponse toPendingResponse(SettlementPendingRow row, Member producer,
            GroupBuy groupBuy) {
        long totalSales = row.getTotalSales();
        long platformFee = BigDecimal.valueOf(totalSales).multiply(platformFeeRate)
                .setScale(0, RoundingMode.FLOOR).longValueExact();
        return new SettlementListItemResponse(
                null, row.getGroupBuyId(), row.getGroupBuyTitle(), row.getProducerId(),
                producer != null ? producer.getName() : null,
                row.getItemCount().intValue(), totalSales, platformFee, totalSales - platformFee,
                SettlementListStatus.PENDING,
                groupBuy != null ? groupBuy.getFinalizedAt() : null,
                null);
    }

    private Page<SettlementListItemResponse> toCompletedPage(Page<Settlement> settlements) {
        Map<Long, Member> membersById =
                loadMembers(settlements.getContent().stream().map(Settlement::getProducerId).toList());
        Map<Long, GroupBuy> groupBuysById =
                loadGroupBuys(settlements.getContent().stream().map(Settlement::getGroupBuyId).toList());
        return settlements.map(settlement -> toCompletedResponse(settlement,
                membersById.get(settlement.getProducerId()), groupBuysById.get(settlement.getGroupBuyId())));
    }

    private List<SettlementListItemResponse> toCompletedResponses(List<Settlement> settlements) {
        Map<Long, Member> membersById = loadMembers(settlements.stream().map(Settlement::getProducerId).toList());
        Map<Long, GroupBuy> groupBuysById =
                loadGroupBuys(settlements.stream().map(Settlement::getGroupBuyId).toList());
        return settlements.stream()
                .map(settlement -> toCompletedResponse(settlement, membersById.get(settlement.getProducerId()),
                        groupBuysById.get(settlement.getGroupBuyId())))
                .toList();
    }

    private SettlementListItemResponse toCompletedResponse(Settlement settlement, Member producer,
            GroupBuy groupBuy) {
        return new SettlementListItemResponse(
                settlement.getId(), settlement.getGroupBuyId(),
                groupBuy != null ? groupBuy.getTitle() : null,
                settlement.getProducerId(), producer != null ? producer.getName() : null,
                settlement.getItemCount(), settlement.getTotalSales(), settlement.getPlatformFee(),
                settlement.getPayout(), SettlementListStatus.COMPLETED,
                groupBuy != null ? groupBuy.getFinalizedAt() : null,
                settlement.getConfirmedAt());
    }

    private Map<Long, GroupBuy> loadGroupBuys(List<Long> groupBuyIds) {
        return groupBuyRepository.findAllById(groupBuyIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(GroupBuy::getId, Function.identity()));
    }

    private Map<Long, Member> loadMembers(List<Long> memberIds) {
        return memberRepository.findAllById(memberIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    // "전체"(대기중+완료 병합) 조회용 - 메모리에 모은 리스트를 요청받은 페이지 크기로 자른다
    private Page<SettlementListItemResponse> paginate(List<SettlementListItemResponse> all, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= all.size()) {
            return new PageImpl<>(List.of(), pageable, all.size());
        }
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
    }
}
