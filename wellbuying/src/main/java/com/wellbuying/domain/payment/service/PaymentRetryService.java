package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.entity.OrderStatus;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.event.PaymentCompletedEvent;
import com.wellbuying.domain.payment.event.PaymentEventPublisher;
import com.wellbuying.domain.payment.event.PaymentFailedEvent;
import com.wellbuying.domain.payment.gateway.BillingCredential;
import com.wellbuying.domain.payment.gateway.BillingKeyProvider;
import com.wellbuying.domain.payment.gateway.PaymentGateway;
import com.wellbuying.domain.payment.gateway.PgApproveCommand;
import com.wellbuying.domain.payment.gateway.PgApprovalException;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// 결제 실패한 주문을 구매자가 직접 재시도하는 흐름을 조립한다. PaymentProcessor(카프카 이벤트 기반)와 순서는
// 같지만(준비 -> PG 승인 -> 결과 반영 -> 이벤트 발행), 이벤트 대신 사용자 요청으로 시작된다는 점이 다르다.
// PG 호출을 트랜잭션 밖에 두는 이유는 PaymentProcessor와 동일
@Component
public class PaymentRetryService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRetryService.class);

    private final OrderRepository orderRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentGateway paymentGateway;
    private final BillingKeyProvider billingKeyProvider;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentRetryService(OrderRepository orderRepository, GroupBuyPartRepository groupBuyPartRepository,
            GroupBuyRepository groupBuyRepository, PaymentTransactionService paymentTransactionService,
            PaymentGateway paymentGateway, BillingKeyProvider billingKeyProvider,
            PaymentEventPublisher paymentEventPublisher) {
        this.orderRepository = orderRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.paymentTransactionService = paymentTransactionService;
        this.paymentGateway = paymentGateway;
        this.billingKeyProvider = billingKeyProvider;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    // 반환값은 재시도로 새로 만들어진 주문의 orderId - 성공/실패 여부와 무관하게 항상 새 주문이 하나 생긴다
    // (실패했던 원래 주문/결제 행은 이력으로 그대로 남는다)
    public String retry(Long memberId, String failedOrderId) {
        Order failedOrder = orderRepository.findByOrderIdAndMemberId(failedOrderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (failedOrder.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_RETRYABLE);
        }

        GroupBuyPart part = groupBuyPartRepository.findById(failedOrder.getGroupBuyParticipantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        GroupBuy groupBuy = groupBuyRepository.findById(part.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        BillingCredential credential = billingKeyProvider.findBillingKey(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND));

        // 이전 시도의 멱등키를 그대로 쓰면 토스가 실패했던 예전 결과를 그대로 돌려줄 수 있으므로,
        // 재시도는 매번 새로운 멱등키로 완전히 새 승인 시도를 만든다
        String idempotencyKey = "ManualRetry:" + failedOrderId + ":" + UUID.randomUUID();
        PaymentPreparation preparation = paymentTransactionService.prepareRetry(failedOrder, paymentGateway.provider(),
                idempotencyKey);

        PgApproveResult result;
        try {
            result = paymentGateway.approve(new PgApproveCommand(
                    credential.billingKey(),
                    credential.customerKey(),
                    preparation.orderId(),
                    "공동구매 결제",
                    failedOrder.getTotalPrice(),
                    idempotencyKey));
        } catch (PgApprovalException e) {
            log.warn("결제 재시도 PG 승인 실패 - orderId={}", preparation.orderId(), e);
            paymentTransactionService.markFailed(preparation.paymentId(), preparation.orderId());
            paymentEventPublisher.publishFailed(PaymentFailedEvent.of(preparation.paymentId(), groupBuy.getId(),
                    failedOrder.getGroupBuyParticipantId(), memberId, failedOrder.getTotalPrice(), e.getMessage()));
            return preparation.orderId();
        }

        Order newOrder = paymentTransactionService.completeApproval(preparation.paymentId(), preparation.orderId(),
                result);
        paymentEventPublisher.publishCompleted(
                PaymentCompletedEvent.of(newOrder, groupBuy.getId(), groupBuy.getProducerId(), result.pgTransactionId()));
        return newOrder.getOrderId();
    }
}
