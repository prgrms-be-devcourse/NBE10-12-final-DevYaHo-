package com.wellbuying.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementItemRepository settlementItemRepository;
    @Mock
    private GroupBuyRepository groupBuyRepository;
    @Mock
    private MemberRepository memberRepository;

    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.05");

    private SettlementQueryService service() {
        return new SettlementQueryService(settlementRepository, settlementItemRepository, groupBuyRepository,
                memberRepository, PLATFORM_FEE_RATE);
    }

    private final Pageable pageable = PageRequest.of(0, 20);

    private Settlement settlement(long groupBuyId, long producerId) {
        return Settlement.confirm(groupBuyId, producerId, 3, 100_000L, 5_000L);
    }

    private void stubJoins(long groupBuyId, String title, LocalDateTime finalizedAt, long producerId,
            String producerName) {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getId()).thenReturn(groupBuyId);
        // getTitle()/getFinalizedAt() 둘 다 호출부(PENDING/COMPLETED/관리자)에 따라 쓰임이 갈려 lenient
        // 처리한다 - PENDING은 제목을 row.getGroupBuyTitle()에서 가져와 groupBuy.getTitle()을 안 쓰고,
        // 관리자 조회는 finalizedAt을 아예 안 쓴다
        lenient().when(groupBuy.getTitle()).thenReturn(title);
        lenient().when(groupBuy.getFinalizedAt()).thenReturn(finalizedAt);
        when(groupBuyRepository.findAllById(any())).thenReturn(List.of(groupBuy));

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(producerId);
        when(member.getName()).thenReturn(producerName);
        when(memberRepository.findAllById(any())).thenReturn(List.of(member));
    }

    private SettlementPendingRow pendingRow(long groupBuyId, String title, long producerId, long itemCount,
            long totalSales) {
        SettlementPendingRow row = mock(SettlementPendingRow.class);
        when(row.getGroupBuyId()).thenReturn(groupBuyId);
        when(row.getGroupBuyTitle()).thenReturn(title);
        when(row.getProducerId()).thenReturn(producerId);
        when(row.getItemCount()).thenReturn(itemCount);
        when(row.getTotalSales()).thenReturn(totalSales);
        return row;
    }

    @Test
    void PENDING_필터는_settlement_item_집계로_예상_지급액을_계산한다() {
        LocalDateTime finalizedAt = YearMonth.now().atDay(3).atStartOfDay();
        SettlementPendingRow row = pendingRow(42L, "제주 감귤 공동구매", 5L, 3L, 100_000L);
        when(settlementItemRepository.findPendingByProducerIdAndFinalizedAtRange(eq(5L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));
        stubJoins(42L, "제주 감귤 공동구매", finalizedAt, 5L, "푸른살림");

        Page<SettlementListItemResponse> page =
                service().getMySettlements(5L, null, null, SettlementListStatus.PENDING, pageable);

        SettlementListItemResponse responseRow = page.getContent().get(0);
        assertThat(responseRow.settlementId()).isNull();
        assertThat(responseRow.groupBuyId()).isEqualTo(42L);
        assertThat(responseRow.groupBuyTitle()).isEqualTo("제주 감귤 공동구매");
        assertThat(responseRow.producerName()).isEqualTo("푸른살림");
        assertThat(responseRow.itemCount()).isEqualTo(3);
        assertThat(responseRow.totalSales()).isEqualTo(100_000L);
        assertThat(responseRow.platformFee()).isEqualTo(5_000L);
        assertThat(responseRow.payout()).isEqualTo(95_000L);
        assertThat(responseRow.status()).isEqualTo(SettlementListStatus.PENDING);
        assertThat(responseRow.finalizedAt()).isEqualTo(finalizedAt);
        assertThat(responseRow.confirmedAt()).isNull();

        verify(settlementRepository, never()).findByProducerIdAndGroupBuyFinalizedAtRange(any(), any(), any(),
                any());
    }

    @Test
    void COMPLETED_필터는_settlement_확정행을_그대로_옮긴다() {
        LocalDateTime finalizedAt = YearMonth.now().atDay(3).atStartOfDay();
        when(settlementRepository.findByProducerIdAndGroupBuyFinalizedAtRange(eq(5L), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(settlement(42L, 5L))));
        stubJoins(42L, "제주 감귤 공동구매", finalizedAt, 5L, "푸른살림");

        Page<SettlementListItemResponse> page =
                service().getMySettlements(5L, null, null, SettlementListStatus.COMPLETED, pageable);

        SettlementListItemResponse row = page.getContent().get(0);
        assertThat(row.groupBuyId()).isEqualTo(42L);
        assertThat(row.groupBuyTitle()).isEqualTo("제주 감귤 공동구매");
        assertThat(row.producerName()).isEqualTo("푸른살림");
        assertThat(row.totalSales()).isEqualTo(100_000L);
        assertThat(row.platformFee()).isEqualTo(5_000L);
        assertThat(row.payout()).isEqualTo(95_000L);
        assertThat(row.status()).isEqualTo(SettlementListStatus.COMPLETED);
        assertThat(row.finalizedAt()).isEqualTo(finalizedAt);
        assertThat(row.confirmedAt()).isNotNull();

        verify(settlementItemRepository, never()).findPendingByProducerIdAndFinalizedAtRange(any(), any(), any(),
                any());
    }

    @Test
    void status_생략시_대기중과_완료를_합쳐_finalizedAt_역순으로_정렬한다() {
        LocalDateTime older = YearMonth.now().atDay(3).atStartOfDay();
        LocalDateTime newer = YearMonth.now().atDay(10).atStartOfDay();

        SettlementPendingRow pending = pendingRow(42L, "제주 감귤 공동구매", 5L, 3L, 100_000L);
        Settlement completed = settlement(43L, 5L);

        when(settlementItemRepository.findPendingByProducerIdAndFinalizedAtRange(eq(5L), any(), any(),
                eq(Pageable.unpaged()))).thenReturn(new PageImpl<>(List.of(pending)));
        when(settlementRepository.findByProducerIdAndGroupBuyFinalizedAtRange(eq(5L), any(), any(),
                eq(Pageable.unpaged()))).thenReturn(new PageImpl<>(List.of(completed)));

        // pendingGroupBuy.getTitle()은 스텁하지 않는다 - PENDING 응답의 제목은 groupBuy가 아니라
        // settlement_item 집계 행(SettlementPendingRow.getGroupBuyTitle()) 쪽에서 온다
        GroupBuy pendingGroupBuy = mock(GroupBuy.class);
        when(pendingGroupBuy.getId()).thenReturn(42L);
        when(pendingGroupBuy.getFinalizedAt()).thenReturn(older);
        GroupBuy completedGroupBuy = mock(GroupBuy.class);
        when(completedGroupBuy.getId()).thenReturn(43L);
        when(completedGroupBuy.getTitle()).thenReturn("전남 김 공동구매");
        when(completedGroupBuy.getFinalizedAt()).thenReturn(newer);
        when(groupBuyRepository.findAllById(any())).thenReturn(List.of(pendingGroupBuy, completedGroupBuy));

        Member producer = mock(Member.class);
        when(producer.getId()).thenReturn(5L);
        when(producer.getName()).thenReturn("푸른살림");
        when(memberRepository.findAllById(any())).thenReturn(List.of(producer));

        Page<SettlementListItemResponse> page = service().getMySettlements(5L, null, null, null, pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).groupBuyId()).isEqualTo(43L);
        assertThat(page.getContent().get(0).status()).isEqualTo(SettlementListStatus.COMPLETED);
        assertThat(page.getContent().get(1).groupBuyId()).isEqualTo(42L);
        assertThat(page.getContent().get(1).status()).isEqualTo(SettlementListStatus.PENDING);
    }

    @Test
    void year_month가_주어지면_해당_달_범위로_조회한다() {
        when(settlementItemRepository.findPendingByProducerIdAndFinalizedAtRange(eq(5L),
                eq(LocalDateTime.of(2026, 3, 1, 0, 0)), eq(LocalDateTime.of(2026, 4, 1, 0, 0)), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service().getMySettlements(5L, 2026, 3, SettlementListStatus.PENDING, pageable);

        verify(settlementItemRepository).findPendingByProducerIdAndFinalizedAtRange(eq(5L),
                eq(LocalDateTime.of(2026, 3, 1, 0, 0)), eq(LocalDateTime.of(2026, 4, 1, 0, 0)), any());
    }

    @Test
    void 조인_대상_공동구매나_판매자가_없으면_판매자명과_성사일은_null이다() {
        // groupBuyTitle은 PENDING 행 자체(row.getGroupBuyTitle())에서 오므로 groupBuy 조인 실패와 무관하게
        // 채워진다 - producerName(Member 조인)과 finalizedAt(GroupBuy 조인)만 null이 된다
        SettlementPendingRow pendingRow = pendingRow(42L, "제주 감귤 공동구매", 5L, 3L, 100_000L);
        when(settlementItemRepository.findPendingByProducerIdAndFinalizedAtRange(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(pendingRow)));
        when(groupBuyRepository.findAllById(any())).thenReturn(List.of());
        when(memberRepository.findAllById(any())).thenReturn(List.of());

        SettlementListItemResponse row = service()
                .getMySettlements(5L, null, null, SettlementListStatus.PENDING, pageable)
                .getContent().get(0);

        assertThat(row.groupBuyTitle()).isEqualTo("제주 감귤 공동구매");
        assertThat(row.producerName()).isNull();
        assertThat(row.groupBuyId()).isEqualTo(42L);
        assertThat(row.finalizedAt()).isNull();
    }

    @Test
    void 관리자_조회는_status가_있으면_findByStatus로_거른다() {
        when(settlementRepository.findByStatus(eq(SettlementStatus.CONFIRMED), any()))
                .thenReturn(new PageImpl<>(List.of(settlement(42L, 5L))));
        stubJoins(42L, "제주 감귤 공동구매", LocalDateTime.now(), 5L, "푸른살림");

        Page<SettlementResponse> page = service().getAllSettlements(SettlementStatus.CONFIRMED, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(settlementRepository).findByStatus(eq(SettlementStatus.CONFIRMED), any());
        verify(settlementRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void 관리자_조회는_status가_null이면_findAll로_전체를_가져온다() {
        when(settlementRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(settlement(42L, 5L))));
        stubJoins(42L, "제주 감귤 공동구매", LocalDateTime.now(), 5L, "푸른살림");

        service().getAllSettlements(null, pageable);

        verify(settlementRepository).findAll(any(Pageable.class));
        verify(settlementRepository, never()).findByStatus(any(), any());
    }
}
