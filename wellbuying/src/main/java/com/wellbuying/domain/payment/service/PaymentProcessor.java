package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.payment.entity.Order;
import com.wellbuying.domain.payment.entity.PaymentFailureType;
import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import com.wellbuying.domain.payment.event.PaymentCompletedEvent;
import com.wellbuying.domain.payment.event.PaymentEventPublisher;
import com.wellbuying.domain.payment.event.PaymentFailedEvent;
import com.wellbuying.domain.payment.gateway.BillingCredential;
import com.wellbuying.domain.payment.gateway.BillingKeyProvider;
import com.wellbuying.domain.payment.gateway.PaymentGateway;
import com.wellbuying.domain.payment.gateway.PgApproveCommand;
import com.wellbuying.domain.payment.gateway.PgApprovalException;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.domain.payment.repository.PaymentConsumedEventRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// 성사 이벤트 1건의 결제 처리 흐름을 조립한다.
// 이 클래스 자체에는 @Transactional을 걸지 않는다 - PG 호출을 트랜잭션 밖에 두는 것이 이 설계의 핵심이라,
// 트랜잭션 구간은 전부 PaymentTransactionService의 메서드 호출로만 열리고 닫힌다.
// 이벤트 발행도 트랜잭션 메서드가 반환된 뒤(=커밋 후)에 하므로 별도의 afterCommit 훅이 필요 없다
@Component
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentTransactionService paymentTransactionService;
    private final PaymentFailureRecorder paymentFailureRecorder;
    private final PaymentConsumedEventRepository paymentConsumedEventRepository;
    private final PaymentGateway paymentGateway;
    private final BillingKeyProvider billingKeyProvider;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentProcessor(PaymentTransactionService paymentTransactionService,
            PaymentFailureRecorder paymentFailureRecorder, PaymentConsumedEventRepository paymentConsumedEventRepository,
            PaymentGateway paymentGateway, BillingKeyProvider billingKeyProvider,
            PaymentEventPublisher paymentEventPublisher) {
        this.paymentTransactionService = paymentTransactionService;
        this.paymentFailureRecorder = paymentFailureRecorder;
        this.paymentConsumedEventRepository = paymentConsumedEventRepository;
        this.paymentGateway = paymentGateway;
        this.billingKeyProvider = billingKeyProvider;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    public void process(GroupBuyCompletedMessage message) {
        // 1차 방어 - 이미 처리한 이벤트면 건너뛴다
        if (paymentConsumedEventRepository.existsByEventId(message.eventId())) {
            log.debug("이미 처리된 성사 이벤트 - eventId={}", message.eventId());
            return;
        }

        PaymentPreparation preparation;
        try {
            preparation = paymentTransactionService.prepare(message, paymentGateway.provider());
        } catch (DataIntegrityViolationException e) {
            // 2차 방어 - 같은 이벤트를 동시에 두 번 받아 위 체크를 나란히 통과한 경우 UNIQUE 제약에 걸린다
            log.info("동시 중복 수신으로 결제 생성이 거부됨 (정상 동작) - eventId={}", message.eventId());
            return;
        }

        if (preparation.failed()) {
            publishFailed(message, preparation.paymentId(), preparation.failureReason());
            return;
        }

        Optional<BillingCredential> credential = billingKeyProvider.findBillingKey(message.memberId());
        if (credential.isEmpty()) {
            paymentTransactionService.markFailed(preparation.paymentId());
            publishFailed(message, preparation.paymentId(), "등록된 빌링키가 없음");
            return;
        }

        PgApproveResult result;
        try {
            result = paymentGateway.approve(toApproveCommand(message, credential.get()));
        } catch (PgApprovalException e) {
            log.warn("PG 승인 실패 - eventId={}, paymentId={}", message.eventId(), preparation.paymentId(), e);
            paymentTransactionService.markFailed(preparation.paymentId());
            publishFailed(message, preparation.paymentId(), e.getMessage());
            return;
        }

        Order order;
        try {
            order = paymentTransactionService.completeApproval(preparation.paymentId(), result,
                    preparation.shippingAddress());
        } catch (OrderCreationException e) {
            // 여기서부터는 실제 결제가 끝난 뒤다 - 되돌리지 않고 기록만 남겨 사람이 처리한다 (보상 트랜잭션 미채택)
            recordApprovedButNotPersisted(PaymentFailureType.ORDER_CREATE_FAILED, message, preparation, result, e);
            return;
        } catch (RuntimeException e) {
            recordApprovedButNotPersisted(PaymentFailureType.APPROVE_RESULT_PERSIST_FAILED, message, preparation,
                    result, e);
            return;
        }

        paymentEventPublisher.publishCompleted(
                PaymentCompletedEvent.of(order, message.groupBuyId(), message.producerId(), result.pgTransactionId()));
    }

    private PgApproveCommand toApproveCommand(GroupBuyCompletedMessage message, BillingCredential credential) {
        return new PgApproveCommand(
                credential.billingKey(),
                // 발급 때 쓴 값을 그대로 보내야 한다 - 승인 시점에 새로 만들면 토스가 다른 고객으로 보고 거부한다
                credential.customerKey(),
                // 토스 orderId는 영문/숫자/-/_ 만 허용하므로 eventId(콜론 포함)를 그대로 쓰지 않는다
                "gb-" + message.partId(),
                "공동구매 결제",
                message.totalAmount(),
                message.eventId());
    }

    private void recordApprovedButNotPersisted(PaymentFailureType failureType, GroupBuyCompletedMessage message,
            PaymentPreparation preparation, PgApproveResult result, Throwable cause) {
        log.error("PG 승인 후 DB 반영 실패 - 수동 확인 필요. eventId={}, paymentId={}, pgTransactionId={}",
                message.eventId(), preparation.paymentId(), result.pgTransactionId(), cause);
        paymentFailureRecorder.record(failureType, message, preparation.paymentId(), result.pgTransactionId(), cause);
    }

    private void publishFailed(GroupBuyCompletedMessage message, Long paymentId, String reason) {
        paymentEventPublisher.publishFailed(PaymentFailedEvent.of(paymentId, message.groupBuyId(), message.partId(),
                message.memberId(), message.totalAmount(), reason));
    }
}
