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
        // 로그 기록 자체가 실패해도 컨슈머를 멈추지 않는다 - 대신 반드시 ERROR 로그로 남겨 사람이 볼 수 있게 한다
        try {
            paymentFailureLogRepository.save(PaymentFailureLog.of(
                    failureType,
                    message.eventId(),
                    message.partId(),
                    message.memberId(),
                    paymentId,
                    pgTransactionId,
                    message.totalAmount(),
                    cause == null ? null : cause.toString()));
        } catch (RuntimeException e) {
            log.error("결제 실패 로그 기록 실패 - 수동 확인 필요. eventId={}, paymentId={}, pgTransactionId={}, amount={}",
                    message.eventId(), paymentId, pgTransactionId, message.totalAmount(), e);
        }
    }
}
