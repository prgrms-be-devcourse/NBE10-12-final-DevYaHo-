package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.entity.PaymentFailureLog;
import com.wellbuying.domain.payment.entity.PaymentFailureType;
import com.wellbuying.domain.payment.entity.PaymentStatus;
import com.wellbuying.domain.payment.event.PaymentEventContext;
import com.wellbuying.domain.payment.gateway.BillingCredential;
import com.wellbuying.domain.payment.gateway.BillingKeyProvider;
import com.wellbuying.domain.payment.gateway.PaymentGateway;
import com.wellbuying.domain.payment.gateway.PgApprovalException;
import com.wellbuying.domain.payment.gateway.PgApproveCommand;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.domain.payment.gateway.PgInquiryException;
import com.wellbuying.domain.payment.gateway.PgInquiryResult;
import com.wellbuying.domain.payment.repository.PaymentFailureLogRepository;
import com.wellbuying.domain.payment.repository.PaymentRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// UNCONFIRMED로 남은 Payment(PG 타임아웃/5xx 재시도 소진, 실제 승인 여부 불명)를 결제조회로 대조해
// 정리한다. 구매자 책임(카드 문제 등)이 아니라 시스템/PG 쪽 사정이므로 구매자에게 재결제를 맡기지 않고
// 이 배치가 대신 확인·정리한다. 한 사이클 안에서 반드시 APPROVED 또는 FAILED로 종결시킨다
// (09-pg-timeout-retry.md)
@Component
public class PaymentUnconfirmedReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentUnconfirmedReconciliationJob.class);

    // 빌링키 자동결제는 카드사 인증 단계가 없어 정상적으로는 이 상태들이 오래 지속되지 않는다.
    // 조회 시점에 이 상태가 나오면 "아직 결론이 안 난 것"으로 보고 이번 주기는 건너뛴다
    private static final Set<String> IN_PROGRESS_STATUSES = Set.of("READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT");

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentGateway paymentGateway;
    private final BillingKeyProvider billingKeyProvider;
    private final PaymentFailureLogRepository paymentFailureLogRepository;

    public PaymentUnconfirmedReconciliationJob(PaymentRepository paymentRepository, OrderRepository orderRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyRepository groupBuyRepository,
            PaymentTransactionService paymentTransactionService, PaymentGateway paymentGateway,
            BillingKeyProvider billingKeyProvider, PaymentFailureLogRepository paymentFailureLogRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.paymentTransactionService = paymentTransactionService;
        this.paymentGateway = paymentGateway;
        this.billingKeyProvider = billingKeyProvider;
        this.paymentFailureLogRepository = paymentFailureLogRepository;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay-ms:60000}")
    public void reconcile() {
        List<Payment> unconfirmed = paymentRepository.findByStatus(PaymentStatus.UNCONFIRMED);
        for (Payment payment : unconfirmed) {
            try {
                reconcileOne(payment.getId());
            } catch (RuntimeException e) {
                log.error("UNCONFIRMED 정리 중 예외 발생 - 다음 주기에 재시도. paymentId={}", payment.getId(), e);
            }
        }
    }

    private void reconcileOne(Long paymentId) {
        if (paymentRepository.claimForReconciliation(paymentId) == 0) {
            // 다른 인스턴스가 이미 점유했거나, 그 사이 다른 경로로 상태가 바뀐 것
            return;
        }

        boolean resolved = false;
        try {
            resolved = tryResolve(paymentId);
        } finally {
            if (!resolved) {
                paymentRepository.releaseReconciliationClaim(paymentId);
            }
        }
    }

    // true를 반환하면 APPROVED/FAILED로 확정된 것(선점 해제 불필요), false면 다음 주기에 다시 본다(선점 해제 필요)
    private boolean tryResolve(Long paymentId) {
        Optional<Order> maybeOrder = orderRepository.findByPaymentId(paymentId);
        if (maybeOrder.isEmpty()) {
            log.error("UNCONFIRMED Payment에 대응하는 Order를 찾지 못함 - paymentId={}", paymentId);
            return false;
        }
        Order order = maybeOrder.get();

        PgInquiryResult inquiry;
        try {
            inquiry = paymentGateway.inquire(order.getOrderId());
        } catch (PgInquiryException e) {
            log.warn("결제조회 호출 실패 - 다음 주기에 재시도. orderId={}", order.getOrderId(), e);
            return false;
        }

        if (IN_PROGRESS_STATUSES.contains(inquiry.status())) {
            log.warn("결제조회 결과가 진행 중 상태 - 다음 주기에 재확인. orderId={}, status={}", order.getOrderId(),
                    inquiry.status());
            return false;
        }

        PaymentEventContext ctx = eventContext(order);

        if (inquiry.isApproved()) {
            paymentTransactionService.completeApproval(paymentId, order.getOrderId(), ctx, inquiry.toApproveResult());
            resolvePaymentFailureLog(paymentId);
            return true;
        }

        // 미승인 확인됨 - 자동 재시도 1회. 조회는 부작용이 없지만 승인 호출은 실제 돈이 움직이는 액션이라
        // 여기서 실패하면(거절이든 재타임아웃이든) 더 반복하지 않고 바로 FAILED로 확정한다
        return retryOnce(paymentId, order, ctx);
    }

    private boolean retryOnce(Long paymentId, Order order, PaymentEventContext ctx) {
        Optional<BillingCredential> credential = billingKeyProvider.findBillingKey(order.getMemberId());
        if (credential.isEmpty()) {
            paymentTransactionService.markFailed(paymentId, order.getOrderId(), ctx, "등록된 빌링키가 없음");
            resolvePaymentFailureLog(paymentId);
            return true;
        }

        // 결제조회로 "미승인"이 확인된 뒤의 완전히 새로운 시도이므로, PaymentRetryService의 수동 재결제와
        // 같은 원칙으로 새 Idempotency-Key를 쓴다 (이전 키를 재사용하면 PG가 예전 결과를 그대로 돌려줄 수 있다)
        String retryIdempotencyKey = "Reconciliation:" + order.getOrderId() + ":" + UUID.randomUUID();
        try {
            PgApproveResult result = paymentGateway.approve(new PgApproveCommand(
                    credential.get().billingKey(),
                    credential.get().customerKey(),
                    order.getOrderId(),
                    "공동구매 결제",
                    order.getTotalPrice(),
                    retryIdempotencyKey));
            paymentTransactionService.completeApproval(paymentId, order.getOrderId(), ctx, result);
        } catch (PgApprovalException e) {
            log.warn("UNCONFIRMED 자동 재시도 실패 - FAILED로 확정. orderId={}", order.getOrderId(), e);
            paymentTransactionService.markFailed(paymentId, order.getOrderId(), ctx, e.getMessage());
        }
        resolvePaymentFailureLog(paymentId);
        return true;
    }

    private PaymentEventContext eventContext(Order order) {
        GroupBuyPart part = groupBuyPartRepository.findById(order.getGroupBuyParticipantId())
                .orElseThrow(() -> new IllegalStateException(
                        "참여 건을 찾지 못함 - groupBuyParticipantId=" + order.getGroupBuyParticipantId()));
        GroupBuy groupBuy = groupBuyRepository.findById(part.getGroupBuyId())
                .orElseThrow(() -> new IllegalStateException("공동구매를 찾지 못함 - groupBuyId=" + part.getGroupBuyId()));
        return new PaymentEventContext(groupBuy.getId(), groupBuy.getProducerId());
    }

    private void resolvePaymentFailureLog(Long paymentId) {
        List<PaymentFailureLog> logs = paymentFailureLogRepository.findByPaymentIdAndFailureTypeAndResolvedFalse(
                paymentId, PaymentFailureType.APPROVAL_UNCONFIRMED_AFTER_TIMEOUT);
        logs.forEach(PaymentFailureLog::resolve);
        paymentFailureLogRepository.saveAll(logs);
    }
}
