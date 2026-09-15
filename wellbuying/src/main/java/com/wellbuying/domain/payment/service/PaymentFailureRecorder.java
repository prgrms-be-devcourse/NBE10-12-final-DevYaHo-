package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.payment.entity.PaymentFailureLog;
import com.wellbuying.domain.payment.entity.PaymentFailureType;
import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import com.wellbuying.domain.payment.repository.PaymentFailureLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// PG 승인은 성공했는데 DB 반영에 실패한 건을 남긴다.
// REQUIRES_NEW가 핵심 - 실패한 트랜잭션에 얹으면 기록까지 같이 롤백돼서 아무 흔적도 남지 않는다
@Component
public class PaymentFailureRecorder {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailureRecorder.class);

    private final PaymentFailureLogRepository paymentFailureLogRepository;

    public PaymentFailureRecorder(PaymentFailureLogRepository paymentFailureLogRepository) {
        this.paymentFailureLogRepository = paymentFailureLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PaymentFailureType failureType, GroupBuyCompletedMessage message, Long paymentId,
            String pgTransactionId, Throwable cause) {
        record(failureType, message.eventId(), message.partId(), message.memberId(), paymentId, pgTransactionId,
                message.totalAmount(), cause);
    }

    // 카프카 이벤트가 없는 경로(PaymentRetryService의 수동 재결제, PaymentUnconfirmedReconciliationJob의
    // 자동 재시도)에서도 쓸 수 있도록 GroupBuyCompletedMessage 대신 필요한 값만 직접 받는 버전 (09-pg-timeout-retry.md)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(PaymentFailureType failureType, String eventId, Long groupBuyParticipantId, Long memberId,
            Long paymentId, String pgTransactionId, int amount, Throwable cause) {
        // 로그 기록 자체가 실패해도 호출자를 멈추지 않는다 - 대신 반드시 ERROR 로그로 남겨 사람이 볼 수 있게 한다
        try {
            paymentFailureLogRepository.save(PaymentFailureLog.of(
                    failureType,
                    eventId,
                    groupBuyParticipantId,
                    memberId,
                    paymentId,
                    pgTransactionId,
                    amount,
                    cause == null ? null : cause.toString()));
        } catch (RuntimeException e) {
            log.error("결제 실패 로그 기록 실패 - 수동 확인 필요. eventId={}, paymentId={}, pgTransactionId={}, amount={}",
                    eventId, paymentId, pgTransactionId, amount, e);
        }
    }
}
