package com.wellbuying.domain.settlement.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import com.wellbuying.domain.settlement.repository.SettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 유예기간이 끝난 공동구매 하나의 정산을 확정한다.
// settlement_item(ACCRUED)을 CONFIRMED로 전이한 뒤, 확정된 합계로 settlement 집계행을 만든다.
// 순서가 중요하다 - 먼저 전이하고 그 결과(CONFIRMED)만 합산해야, 전이와 커밋 사이에 새 결제 완료가
// 끼어들어도 집계행의 합계와 CONFIRMED item 합계가 어긋나지 않는다.
@Service
public class SettlementConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(SettlementConfirmationService.class);

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final BigDecimal platformFeeRate;

    public SettlementConfirmationService(SettlementRepository settlementRepository,
            SettlementItemRepository settlementItemRepository, GroupBuyRepository groupBuyRepository,
            @Value("${settlement.platform-fee-rate:0.05}") BigDecimal platformFeeRate) {
        this.settlementRepository = settlementRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.platformFeeRate = platformFeeRate;
    }

    @Transactional
    public void confirm(Long groupBuyId) {
        if (settlementRepository.existsByGroupBuyId(groupBuyId)) {
            // 배치 동시 실행 등으로 이미 확정된 경우 - UNIQUE 제약과 이중 방어
            log.warn("이미 정산이 확정된 공동구매 - 건너뜀. groupBuyId={}", groupBuyId);
            return;
        }

        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new IllegalStateException(
                        "정산 대상 공동구매를 찾을 수 없음 - groupBuyId=" + groupBuyId));

        int confirmedCount = settlementItemRepository.updateStatusByGroupBuyId(
                groupBuyId, SettlementItemStatus.ACCRUED, SettlementItemStatus.CONFIRMED);
        if (confirmedCount == 0) {
            log.warn("확정할 정산 대상(ACCRUED)이 없는 공동구매 - 건너뜀. groupBuyId={}", groupBuyId);
            return;
        }

        long totalSales = settlementItemRepository.sumAmountByGroupBuyIdAndStatus(
                groupBuyId, SettlementItemStatus.CONFIRMED);
        long platformFee = BigDecimal.valueOf(totalSales)
                .multiply(platformFeeRate)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();

        settlementRepository.save(Settlement.confirm(
                groupBuyId, groupBuy.getProducerId(), confirmedCount, totalSales, platformFee));

        log.info("정산 확정 - groupBuyId={}, items={}, totalSales={}, platformFee={}, payout={}",
                groupBuyId, confirmedCount, totalSales, platformFee, totalSales - platformFee);
    }
}
