package com.wellbuying.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import com.wellbuying.domain.settlement.repository.SettlementRepository;
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
    private GroupBuyRepository groupBuyRepository;
    @Mock
    private MemberRepository memberRepository;

    private SettlementQueryService service() {
        return new SettlementQueryService(settlementRepository, groupBuyRepository, memberRepository);
    }

    private final Pageable pageable = PageRequest.of(0, 20);

    private Settlement settlement(long groupBuyId, long producerId) {
        return Settlement.confirm(groupBuyId, producerId, 3, 100_000L, 5_000L);
    }

    private void stubJoins(long groupBuyId, String title, long producerId, String producerName) {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getId()).thenReturn(groupBuyId);
        when(groupBuy.getTitle()).thenReturn(title);
        when(groupBuyRepository.findAllById(any())).thenReturn(List.of(groupBuy));

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(producerId);
        when(member.getName()).thenReturn(producerName);
        when(memberRepository.findAllById(any())).thenReturn(List.of(member));
    }

    @Test
    void 내_정산_내역은_producerId로_조회해_공동구매_제목과_판매자명을_조합한다() {
        when(settlementRepository.findByProducerId(eq(5L), any()))
                .thenReturn(new PageImpl<>(List.of(settlement(42L, 5L))));
        stubJoins(42L, "제주 감귤 공동구매", 5L, "푸른살림");

        Page<SettlementResponse> page = service().getMySettlements(5L, pageable);

        SettlementResponse row = page.getContent().get(0);
        assertThat(row.groupBuyId()).isEqualTo(42L);
        assertThat(row.groupBuyTitle()).isEqualTo("제주 감귤 공동구매");
        assertThat(row.producerId()).isEqualTo(5L);
        assertThat(row.producerName()).isEqualTo("푸른살림");
        assertThat(row.totalSales()).isEqualTo(100_000L);
        assertThat(row.platformFee()).isEqualTo(5_000L);
        assertThat(row.payout()).isEqualTo(95_000L);
        assertThat(row.status()).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 관리자_조회는_status가_있으면_findByStatus로_거른다() {
        when(settlementRepository.findByStatus(eq(SettlementStatus.CONFIRMED), any()))
                .thenReturn(new PageImpl<>(List.of(settlement(42L, 5L))));
        stubJoins(42L, "제주 감귤 공동구매", 5L, "푸른살림");

        Page<SettlementResponse> page = service().getAllSettlements(SettlementStatus.CONFIRMED, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(settlementRepository).findByStatus(eq(SettlementStatus.CONFIRMED), any());
        verify(settlementRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void 관리자_조회는_status가_null이면_findAll로_전체를_가져온다() {
        when(settlementRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(settlement(42L, 5L))));
        stubJoins(42L, "제주 감귤 공동구매", 5L, "푸른살림");

        service().getAllSettlements(null, pageable);

        verify(settlementRepository).findAll(any(Pageable.class));
        verify(settlementRepository, never()).findByStatus(any(), any());
    }

    @Test
    void 조인_대상_공동구매나_판매자가_없으면_제목과_판매자명은_null이다() {
        when(settlementRepository.findByProducerId(any(), any()))
                .thenReturn(new PageImpl<>(List.of(settlement(42L, 5L))));
        when(groupBuyRepository.findAllById(any())).thenReturn(List.of());
        when(memberRepository.findAllById(any())).thenReturn(List.of());

        SettlementResponse row = service().getMySettlements(5L, pageable).getContent().get(0);

        assertThat(row.groupBuyTitle()).isNull();
        assertThat(row.producerName()).isNull();
        assertThat(row.groupBuyId()).isEqualTo(42L);
    }
}
