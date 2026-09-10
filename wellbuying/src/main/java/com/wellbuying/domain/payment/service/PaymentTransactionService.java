package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.entity.PaymentConsumedEvent;
import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import com.wellbuying.domain.payment.event.PaymentCompletedEvent;
import com.wellbuying.domain.payment.event.PaymentEventContext;
import com.wellbuying.domain.payment.event.PaymentEventPublisher;
import com.wellbuying.domain.payment.event.PaymentFailedEvent;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.domain.payment.repository.PaymentRepository;
import com.wellbuying.domain.payment.repository.PaymentConsumedEventRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 결제 처리의 DB 트랜잭션 구간만 담당한다. PG 호출은 여기 들어오지 않는다 -
// 외부 호출을 트랜잭션 안에 두면 응답이 늦어지는 동안 DB 커넥션을 계속 붙잡게 되기 때문.
// 오케스트레이션(트랜잭션 사이에서 PG를 호출하고 실패를 기록하는 일)은 PaymentProcessor(카프카 경로)와
// PaymentRetryService(수동 재시도 경로)가 한다.
//
// 결제 완료/실패 이벤트 발행은 각 트랜잭션 메서드 안에서 PaymentEventPublisher를 통해 아웃박스 행으로 남긴다 -
// 상태 전이와 발행이 하나의 커밋으로 묶여야 유실이 없기 때문(dual write 문제). 실제 Kafka 발행은
// PaymentOutboxRelay가 별도로 한다. 03-outbox-poller.md 참고
@Service
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentConsumedEventRepository paymentConsumedEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentTransactionService(PaymentRepository paymentRepository, OrderRepository orderRepository,
            PaymentConsumedEventRepository paymentConsumedEventRepository,
            PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentConsumedEventRepository = paymentConsumedEventRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    // TX1 - 처리 이력, READY 결제 건, PENDING 주문을 한 트랜잭션으로 남긴다.
    //
    // 주문을 승인 전에 만드는 이유: TX1이 커밋된 뒤 승인 응답을 받기 전에 서버가 죽으면,
    // 이미 저장된 payment_consumed_event 때문에 카프카가 같은 메시지를 다시 줘도 재처리되지 않는다.
    // 그때 주문 행마저 없으면 구매자에게는 아무 흔적도 남지 않아 수동 개입 전까지 상태를 알 수 없다.
    // PENDING 주문을 미리 만들어 두면 "결제 대기"로 보이고, 나중에 PG 조회로 대조해 복구할 기준도 생긴다.
    //
    // 배송지는 이벤트에 실려 오므로 조회하지 않고, 값이 없으면 승인을 아예 시도하지 않고 FAILED로 끝낸다
    // (돈이 나간 뒤에 막으면 수동 처리 대상이 되므로 그 전에 거른다). 이 실패도 같은 트랜잭션에서
    // 아웃박스 행으로 남겨 알림 도메인이 받을 수 있게 한다.
    // 이 경로에서는 주문을 만들지 않는다 - orders.shipping_address가 NOT NULL이기도 하고,
    // 결제를 시도조차 하지 않은 건이라 구매자에게 보여줄 주문도 아니다
    @Transactional
    public PaymentPreparation prepare(GroupBuyCompletedMessage message, String pgProvider) {
        paymentConsumedEventRepository.save(PaymentConsumedEvent.of(message.eventId(), message.eventType()));

        Payment payment = paymentRepository.save(Payment.ready(
                message.partId(),
                message.memberId(),
                message.totalAmount(),
                pgProvider,
                message.eventId()));

        if (!message.hasShippingAddress()) {
            payment.fail();
            String reason = "이벤트에 배송지가 없음";
            paymentEventPublisher.publishFailed(failedEvent(payment, message.groupBuyId(), reason));
            return PaymentPreparation.failed(payment.getId(), reason);
        }

        Order order = orderRepository.save(Order.pending(
                payment.getId(),
                payment.getGroupBuyParticipantId(),
                payment.getMemberId(),
                message.shippingAddress(),
                payment.getAmount()));
        return PaymentPreparation.ready(payment.getId(), order.getOrderId());
    }

    // 결제 실패 후 구매자가 직접 재시도할 때의 TX1 - 카프카 이벤트가 없으므로 이벤트 중복 방지 기록은 남기지 않고,
    // 실패한 주문의 참여 건/배송지/금액을 그대로 이어받아 완전히 새로운 Payment/Order 쌍을 만든다.
    // 실패했던 주문/결제 행은 손대지 않고 이력으로 남겨둔다
    @Transactional
    public PaymentPreparation prepareRetry(Order failedOrder, String pgProvider, String idempotencyKey) {
        Payment payment = paymentRepository.save(Payment.ready(
                failedOrder.getGroupBuyParticipantId(),
                failedOrder.getMemberId(),
                failedOrder.getTotalPrice(),
                pgProvider,
                idempotencyKey));

        Order order = orderRepository.save(Order.pending(
                payment.getId(),
                failedOrder.getGroupBuyParticipantId(),
                failedOrder.getMemberId(),
                failedOrder.getShippingAddress(),
                payment.getAmount()));
        return PaymentPreparation.ready(payment.getId(), order.getOrderId());
    }

    // TX2 - 승인 결과를 결제와 주문에 함께 반영하고, 결제 완료 이벤트를 같은 트랜잭션에서 아웃박스 행으로 남긴다.
    // 주문은 TX1에서 이미 만들어 뒀으므로 여기서는 INSERT 없이 두 행의 상태만 바꾼다 -
    // 승인은 끝났는데 주문 생성에서 깨지는 경우(수동 처리 대상)를 구조적으로 줄이기 위한 것이다.
    // 이 트랜잭션이 롤백되면 아웃박스 행도 함께 사라지므로 "완료 커밋 = 완료 이벤트 존재" 불변식이 유지된다.
    // 반환하는 Order는 수동 재시도 경로(PaymentRetryService)가 새 주문 상세를 돌려주는 데 쓴다
    @Transactional
    public Order completeApproval(Long paymentId, String orderId, PaymentEventContext ctx, PgApproveResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.approve(result.pgTransactionId(), result.approvedAt());

        Order order = orderRepository.findById(orderId)
                // TX1에서 만든 주문이 없다는 건 정상 흐름에서 나올 수 없는 상태다.
                // 이미 승인이 끝난 뒤이므로 커밋 실패와 구분해 실패 로그에 남긴다
                .orElseThrow(() -> new OrderCreationException(
                        "승인 전에 만들어 둔 주문을 찾지 못함 - orderId=" + orderId));
        order.markPaid();

        paymentEventPublisher.publishCompleted(PaymentCompletedEvent.of(
                order, ctx.groupBuyId(), ctx.producerId(), result.pgTransactionId()));
        return order;
    }

    // PG 승인이 거절됐거나 빌링키가 없을 때 - 결제는 이뤄지지 않았으므로 상태만 FAILED로 남긴다.
    // 미리 만들어 둔 주문도 함께 PAYMENT_FAILED로 내려, 구매자가 주문내역에서 실패를 확인할 수 있게 한다.
    // 실패 이벤트도 같은 트랜잭션에서 아웃박스 행으로 남긴다
    @Transactional
    public void markFailed(Long paymentId, String orderId, PaymentEventContext ctx, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.fail();

        if (orderId != null) {
            orderRepository.findById(orderId).ifPresent(Order::markPaymentFailed);
        }

        paymentEventPublisher.publishFailed(failedEvent(payment, ctx.groupBuyId(), reason));
    }

    // 실패 이벤트에 실을 값 중 groupBuyId만 바깥에서 받고, 참여 건/회원/금액은 방금 로드한 Payment에서 꺼낸다
    private PaymentFailedEvent failedEvent(Payment payment, Long groupBuyId, String reason) {
        return PaymentFailedEvent.of(payment.getId(), groupBuyId, payment.getGroupBuyParticipantId(),
                payment.getMemberId(), payment.getAmount(), reason);
    }
}
