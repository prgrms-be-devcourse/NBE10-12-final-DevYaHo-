package com.wellbuying.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.settlement.dto.SettlementMonthlySummaryResponse;
import com.wellbuying.domain.settlement.dto.SettlementTrendGranularity;
import com.wellbuying.domain.settlement.dto.SettlementTrendPointResponse;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import com.wellbuying.domain.settlement.repository.SettlementTrendRow;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementStatsServiceTest {

    private static final Long PRODUCER_ID = 5L;

    @Mock
    private SettlementItemRepository settlementItemRepository;

    private SettlementStatsService service() {
        return new SettlementStatsService(settlementItemRepository);
    }

    @Test
    void getTrend은_이번_달을_포함해_최근_12개월_1일부터_조회해_점_목록으로_변환한다() {
        LocalDateTime expectedFrom = YearMonth.now().minusMonths(11).atDay(1).atStartOfDay();
        SettlementTrendRow row = mock(SettlementTrendRow.class);
        when(row.getPeriodStart()).thenReturn(LocalDateTime.of(2026, 9, 1, 0, 0));
        when(row.getTotalSales()).thenReturn(100_000L);
        when(row.getItemCount()).thenReturn(5L);
        when(settlementItemRepository.findTrend(eq(PRODUCER_ID), eq("month"), eq(expectedFrom)))
                .thenReturn(List.of(row));

        List<SettlementTrendPointResponse> points = service().getTrend(PRODUCER_ID, SettlementTrendGranularity.MONTHLY);

        assertThat(points).hasSize(1);
        SettlementTrendPointResponse point = points.get(0);
        assertThat(point.periodStart()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(point.totalSales()).isEqualTo(100_000L);
        assertThat(point.itemCount()).isEqualTo(5);
    }

    @Test
    void getTrend은_WEEKLY면_week_단위로_조회한다() {
        when(settlementItemRepository.findTrend(eq(PRODUCER_ID), eq("week"), any())).thenReturn(List.of());

        service().getTrend(PRODUCER_ID, SettlementTrendGranularity.WEEKLY);

        verify(settlementItemRepository).findTrend(eq(PRODUCER_ID), eq("week"), any());
    }

    @Test
    void getMonthlySummary는_이번달_전월_대기중_확정_수치를_모아_하나로_합친다() {
        YearMonth thisMonth = YearMonth.now();
        LocalDateTime monthStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime previousMonthStart = thisMonth.minusMonths(1).atDay(1).atStartOfDay();

        when(settlementItemRepository.sumAmountByProducerIdAndPaidAtRange(PRODUCER_ID, monthStart, nextMonthStart))
                .thenReturn(1_000_000L);
        when(settlementItemRepository.countByProducerIdAndPaidAtRange(PRODUCER_ID, monthStart, nextMonthStart))
                .thenReturn(10L);
        when(settlementItemRepository.sumAmountByProducerIdAndPaidAtRange(PRODUCER_ID, previousMonthStart, monthStart))
                .thenReturn(800_000L);
        when(settlementItemRepository.sumAmountByProducerIdAndStatus(PRODUCER_ID, SettlementItemStatus.ACCRUED))
                .thenReturn(150_000L);
        when(settlementItemRepository.countByProducerIdAndStatus(PRODUCER_ID, SettlementItemStatus.ACCRUED))
                .thenReturn(3L);
        when(settlementItemRepository.sumAmountByProducerIdAndPaidAtRangeAndStatus(PRODUCER_ID, monthStart,
                nextMonthStart, SettlementItemStatus.CONFIRMED)).thenReturn(700_000L);
        when(settlementItemRepository.countByProducerIdAndPaidAtRangeAndStatus(PRODUCER_ID, monthStart,
                nextMonthStart, SettlementItemStatus.CONFIRMED)).thenReturn(6L);

        SettlementMonthlySummaryResponse response = service().getMonthlySummary(PRODUCER_ID);

        assertThat(response.yearMonth()).isEqualTo(thisMonth.toString());
        assertThat(response.thisMonthTotalSales()).isEqualTo(1_000_000L);
        assertThat(response.thisMonthItemCount()).isEqualTo(10);
        assertThat(response.previousMonthTotalSales()).isEqualTo(800_000L);
        assertThat(response.pendingAmount()).isEqualTo(150_000L);
        assertThat(response.pendingItemCount()).isEqualTo(3);
        assertThat(response.thisMonthSettledAmount()).isEqualTo(700_000L);
        assertThat(response.thisMonthSettledItemCount()).isEqualTo(6);
    }
}
