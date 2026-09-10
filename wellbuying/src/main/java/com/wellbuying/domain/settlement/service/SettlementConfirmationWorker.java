package com.wellbuying.domain.settlement.service;

import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 재결제 유예기간(공동구매 finalized_at + grace-period-days)이 끝난 공동구매를 모아 정산을 확정한다.
// 한 번의 실행에서 처리할 최대 건수를 제한하고, 확정 자체는 건별 트랜잭션으로 SettlementConfirmationService에
// 위임한다 (GroupBuyFinalizationWorker와 같은 방식). 처리 못한 나머지는 다음 실행에서 자연스럽게 이어진다.
@Component
public class SettlementConfirmationWorker {

    private static final Logger log = LoggerFactory.getLogger(SettlementConfirmationWorker.class);

    private static final Limit BATCH_LIMIT = Limit.of(200);

    private final SettlementItemRepository settlementItemRepository;
    private final SettlementConfirmationService settlementConfirmationService;
    private final int gracePeriodDays;

    public SettlementConfirmationWorker(SettlementItemRepository settlementItemRepository,
            SettlementConfirmationService settlementConfirmationService,
            @Value("${settlement.confirm.grace-period-days:3}") int gracePeriodDays) {
        this.settlementItemRepository = settlementItemRepository;
        this.settlementConfirmationService = settlementConfirmationService;
        this.gracePeriodDays = gracePeriodDays;
    }

    @Scheduled(fixedDelayString = "${settlement.confirm.fixed-delay-ms:3600000}")
    public void confirmDueSettlements() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(gracePeriodDays);
        List<Long> groupBuyIds = settlementItemRepository.findGroupBuyIdsReadyToConfirm(
                SettlementItemStatus.ACCRUED, threshold, BATCH_LIMIT);
        if (groupBuyIds.isEmpty()) {
            return;
        }

        for (Long groupBuyId : groupBuyIds) {
            try {
                settlementConfirmationService.confirm(groupBuyId);
            } catch (Exception e) {
                // 한 건이 실패해도 나머지 확정은 계속한다. 실패한 건은 상태가 그대로 ACCRUED라 다음 실행에서 다시 잡힌다
                log.error("정산 확정 실패 - groupBuyId={}", groupBuyId, e);
            }
        }
    }
}
