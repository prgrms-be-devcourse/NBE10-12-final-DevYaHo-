package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.payment.entity.Order;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.entity.PaymentConsumedEvent;
import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.domain.payment.repository.OrderRepository;
import com.wellbuying.domain.payment.repository.PaymentRepository;
import com.wellbuying.domain.payment.repository.PaymentConsumedEventRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 결제 처리의 DB 트랜잭션 구간만 담당한다. PG 호출은 여기 들어오지 않는다 -
// 외부 호출을 트랜잭션 안에 두면 응답이 늦어지는 동안 DB 커넥션을 계속 붙잡게 되기 때문.
// 오케스트레이션(트랜잭션 사이에서 PG를 호출하고 실패를 기록하는 일)은 PaymentProcessor가 한다
@Service
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentConsumedEventRepository paymentConsumedEventRepository;

    public PaymentTransactionService(PaymentRepository paymentRepository, OrderRepository orderRepository,
            PaymentConsumedEventRepository paymentConsumedEventRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.paymentConsumedEventRepository = paymentConsumedEventRepository;
    }

    // TX1 - 처리 이력, READY 결제 건, PENDING 주문을 한 트랜잭션으로 남긴다.
    //
    // 주문을 승인 전에 만드는 이유: TX1이 커밋된 뒤 승인 응답을 받기 전에 서버가 죽으면,
    // 이미 저장된 payment_consumed_event 때문에 카프카가 같은 메시지를 다시 줘도 재처리되지 않는다.
    // 그때 주문 행마저 없으면 구매자에게는 아무 흔적도 남지 않아 수동 개입 전까지 상태를 알 수 없다.
    // PENDING 주문을 미리 만들어 두면 "결제 대기"로 보이고, 나중에 PG 조회로 대조해 복구할 기준도 생긴다.
    //
    // 배송지는 이벤트에 실려 오므로 조회하지 않고, 값이 없으면 승인을 아예 시도하지 않고 FAILED로 끝낸다
    // (돈이 나간 뒤에 막으면 수동 처리 대상이 되므로 그 전에 거른다).
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
            return PaymentPreparation.failed(payment.getId(), "이벤트에 배송지가 없음");
        }

        Order order = orderRepository.save(Order.pending(
                payment.getId(),
                payment.getGroupBuyParticipantId(),
                payment.getMemberId(),
                message.shippingAddress(),
                payment.getAmount()));
        return PaymentPreparation.ready(payment.getId(), order.getOrderId());
    }

    // TX2 - 승인 결과를 결제와 주문에 함께 반영한다.
    // 주문은 TX1에서 이미 만들어 뒀으므로 여기서는 INSERT 없이 두 행의 상태만 바꾼다 -
    // 승인은 끝났는데 주문 생성에서 깨지는 경우(수동 처리 대상)를 구조적으로 줄이기 위한 것이다
    @Transactional
    public Order completeApproval(Long paymentId, String orderId, PgApproveResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.approve(result.pgTransactionId(), result.approvedAt());

        Order order = orderRepository.findById(orderId)
                // TX1에서 만든 주문이 없다는 건 정상 흐름에서 나올 수 없는 상태다.
                // 이미 승인이 끝난 뒤이므로 커밋 실패와 구분해 실패 로그에 남긴다
                .orElseThrow(() -> new OrderCreationException(
                        "승인 전에 만들어 둔 주문을 찾지 못함 - orderId=" + orderId));
        order.markPaid();
        return order;
    }

    // PG 승인이 거절됐을 때 - 결제는 이뤄지지 않았으므로 상태만 FAILED로 남긴다.
    // 미리 만들어 둔 주문도 함께 PAYMENT_FAILED로 내려, 구매자가 주문내역에서 실패를 확인할 수 있게 한다
    @Transactional
    public void markFailed(Long paymentId, String orderId) {
        paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))
                .fail();

        if (orderId != null) {
            orderRepository.findById(orderId).ifPresent(Order::markPaymentFailed);
        }
    }
}
