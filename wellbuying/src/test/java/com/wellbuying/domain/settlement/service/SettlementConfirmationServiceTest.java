package com.wellbuying.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import com.wellbuying.domain.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementConfirmationServiceTest {

    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementItemRepository settlementItemRepository;
    @Mock
    private GroupBuyRepository groupBuyRepository;

    private SettlementConfirmationService service() {
        return new SettlementConfirmationService(settlementRepository, settlementItemRepository, groupBuyRepository,
                new BigDecimal("0.05"));
    }

    private void givenConfirmable(long groupBuyId, long producerId, int accruedCount, long sumAmount) {
        when(settlementRepository.existsByGroupBuyId(groupBuyId)).thenReturn(false);
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getProducerId()).thenReturn(producerId);
        when(groupBuyRepository.findById(groupBuyId)).thenReturn(Optional.of(groupBuy));
        when(settlementItemRepository.updateStatusByGroupBuyId(groupBuyId, SettlementItemStatus.ACCRUED,
                SettlementItemStatus.CONFIRMED)).thenReturn(accruedCount);
        when(settlementItemRepository.sumAmountByGroupBuyIdAndStatus(groupBuyId, SettlementItemStatus.CONFIRMED))
                .thenReturn(sumAmount);
    }

    @Test
    void ACCRUED를_CONFIRMED로_전이하고_확정된_합계로_집계행을_만든다() {
        givenConfirmable(42L, 5L, 3, 100_000L);

        service().confirm(42L);

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, times(1)).save(captor.capture());
        Settlement saved = captor.getValue();
        assertThat(saved.getGroupBuyId()).isEqualTo(42L);
        assertThat(saved.getProducerId()).isEqualTo(5L);
        assertThat(saved.getItemCount()).isEqualTo(3);
        assertThat(saved.getTotalSales()).isEqualTo(100_000L);
        assertThat(saved.getPlatformFee()).isEqualTo(5_000L);
        assertThat(saved.getPayout()).isEqualTo(95_000L);
    }

    @Test
    void 플랫폼_수수료는_원_단위를_버림한다() {
        givenConfirmable(42L, 5L, 1, 19_999L);

        service().confirm(42L);

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).save(captor.capture());
        // 19999 * 0.05 = 999.95 -> 999
        assertThat(captor.getValue().getPlatformFee()).isEqualTo(999L);
        assertThat(captor.getValue().getPayout()).isEqualTo(19_000L);
    }

    @Test
    void 이미_확정된_공동구매면_전이도_집계행_생성도_하지_않는다() {
        when(settlementRepository.existsByGroupBuyId(42L)).thenReturn(true);

        service().confirm(42L);

        verify(settlementItemRepository, never()).updateStatusByGroupBuyId(any(), any(), any());
        verify(settlementRepository, never()).save(any());
    }

    @Test
    void 확정할_ACCRUED_항목이_없으면_집계행을_만들지_않는다() {
        when(settlementRepository.existsByGroupBuyId(42L)).thenReturn(false);
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findById(42L)).thenReturn(Optional.of(groupBuy));
        when(settlementItemRepository.updateStatusByGroupBuyId(42L, SettlementItemStatus.ACCRUED,
                SettlementItemStatus.CONFIRMED)).thenReturn(0);

        service().confirm(42L);

        verify(settlementRepository, never()).save(any());
    }
}
