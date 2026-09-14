package com.wellbuying.domain.settlement.service;

import com.wellbuying.domain.settlement.entity.SettlementItem;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.event.PaymentCompletedMessage;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 결제 완료 이벤트를 받아 정산 대상으로 적립한다 (Phase 1).
// 금액 확정(수수료 차감, group_buy 단위 집계)은 하지 않는다 - 유예기간이 끝난 뒤 배치가 한다(Phase 2).
@Service
public class SettlementAccrualService {

    private static final Logger log = LoggerFactory.getLogger(SettlementAccrualService.class);

    private final SettlementItemRepository settlementItemRepository;

    public SettlementAccrualService(SettlementItemRepository settlementItemRepository) {
        this.settlementItemRepository = settlementItemRepository;
    }

    @Transactional
    public void accrue(PaymentCompletedMessage message) {
        // Kafka는 at-least-once라 같은 이벤트가 재소비될 수 있어, 저장 전 존재 여부를 먼저 확인한다.
        // exists 확인과 save 사이의 레이스는 아래 UNIQUE 제약(uk_settlement_item_group_buy_participant_id)이 잡는다
        if (settlementItemRepository.existsByGroupBuyParticipantId(message.groupBuyParticipantId())) {
            log.debug("이미 적립된 결제 건이라 무시 - participantId={}", message.groupBuyParticipantId());
            return;
        }

        // 정산이 이미 확정된 공동구매에 뒤늦게 결제 완료가 도착한 경우.
        // 설계상 payment 쪽 재결제 유예기간 가드가 막아야 하는 경로다(payment/00-payment-design.md,
        // settlement/02-batch-confirm.md). 확정분을 건드리지 않도록 여기서 적립하지 않고,
        // 수동 확인이 필요하다는 신호만 남긴다
        if (settlementItemRepository.existsByGroupBuyIdAndStatus(message.groupBuyId(), SettlementItemStatus.CONFIRMED)) {
            log.error("정산 확정 후 결제 완료 도착 - 적립하지 않음, 수동 확인 필요. groupBuyId={}, participantId={}, amount={}",
                    message.groupBuyId(), message.groupBuyParticipantId(), message.amount());
            return;
        }

        try {
            settlementItemRepository.save(SettlementItem.accrue(
                    message.groupBuyId(),
                    message.groupBuyParticipantId(),
                    message.producerId(),
                    message.memberId(),
                    message.amount(),
                    message.occurredAt()));
        } catch (DataIntegrityViolationException e) {
            // 거의 동시에 같은 이벤트가 두 번 소비돼 exists 확인을 나란히 통과한 경우 -
            // "이미 다른 스레드가 적립했다"는 뜻으로 보고 흡수한다 (NotificationService.save와 같은 처리)
            log.debug("동시 처리로 이미 적립된 결제 건이라 무시 - participantId={}", message.groupBuyParticipantId());
        }
    }
}
