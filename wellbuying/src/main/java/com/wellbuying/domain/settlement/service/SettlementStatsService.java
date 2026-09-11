package com.wellbuying.domain.settlement.service;

import com.wellbuying.domain.settlement.dto.SettlementMonthlySummaryResponse;
import com.wellbuying.domain.settlement.dto.SettlementTrendGranularity;
import com.wellbuying.domain.settlement.dto.SettlementTrendPointResponse;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import com.wellbuying.domain.settlement.repository.SettlementTrendRow;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 정산 대시보드 상단 - 매출 추이 그래프 / 이번 달 요약 카드.
// 전부 settlement_item.paidAt(실제 결제 시점) 기준으로 집계한다 - settlement 확정은 유예기간(기본
// repayment.grace-period-days) + 배치 주기만큼 늦게 일어나서, confirmedAt 기준으로 집계하면 최근
// 며칠 매출이 그래프에서 통째로 빠져 보인다. "매출"은 판매 시점 개념이라 정산 처리 지연과 분리한다.
@Service
public class SettlementStatsService {

    // 그래프는 "이번 달을 포함해 최근 12개월" - 주간/월간 조회 둘 다 같은 창을 쓴다
    private static final long TREND_WINDOW_MONTHS = 12;

    private final SettlementItemRepository settlementItemRepository;

    public SettlementStatsService(SettlementItemRepository settlementItemRepository) {
        this.settlementItemRepository = settlementItemRepository;
    }

    @Transactional(readOnly = true)
    public List<SettlementTrendPointResponse> getTrend(Long producerId, SettlementTrendGranularity granularity) {
        LocalDateTime from = YearMonth.now().minusMonths(TREND_WINDOW_MONTHS - 1).atDay(1).atStartOfDay();
        List<SettlementTrendRow> rows = settlementItemRepository.findTrend(producerId, granularity.truncUnit(), from);
        return rows.stream()
                .map(row -> new SettlementTrendPointResponse(row.getPeriodStart(), row.getTotalSales(),
                        row.getGroupBuyCount().intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SettlementMonthlySummaryResponse getMonthlySummary(Long producerId) {
        YearMonth thisMonth = YearMonth.now();
        LocalDateTime monthStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime previousMonthStart = thisMonth.minusMonths(1).atDay(1).atStartOfDay();

        long thisMonthTotalSales = settlementItemRepository
                .sumAmountByProducerIdAndPaidAtRange(producerId, monthStart, nextMonthStart);
        long thisMonthItemCount = settlementItemRepository
                .countByProducerIdAndPaidAtRange(producerId, monthStart, nextMonthStart);
        long previousMonthTotalSales = settlementItemRepository
                .sumAmountByProducerIdAndPaidAtRange(producerId, previousMonthStart, monthStart);
        long pendingAmount = settlementItemRepository
                .sumAmountByProducerIdAndStatus(producerId, SettlementItemStatus.ACCRUED);
        long pendingItemCount = settlementItemRepository
                .countByProducerIdAndStatus(producerId, SettlementItemStatus.ACCRUED);
        long thisMonthSettledAmount = settlementItemRepository.sumAmountByProducerIdAndPaidAtRangeAndStatus(
                producerId, monthStart, nextMonthStart, SettlementItemStatus.CONFIRMED);
        long thisMonthSettledItemCount = settlementItemRepository.countByProducerIdAndPaidAtRangeAndStatus(
                producerId, monthStart, nextMonthStart, SettlementItemStatus.CONFIRMED);

        return new SettlementMonthlySummaryResponse(
                thisMonth.toString(),
                thisMonthTotalSales, (int) thisMonthItemCount,
                previousMonthTotalSales,
                pendingAmount, (int) pendingItemCount,
                thisMonthSettledAmount, (int) thisMonthSettledItemCount);
    }
}
