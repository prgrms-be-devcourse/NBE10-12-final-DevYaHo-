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

    // TX1 - 처리 이력과 READY 상태 결제 건을 남긴다.
    // 배송지는 이벤트에 실려 오므로 조회하지 않고, 값이 없으면 승인을 아예 시도하지 않고 FAILED로 끝낸다
    // (돈이 나간 뒤에 막으면 수동 처리 대상이 되므로 그 전에 거른다)
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
        return PaymentPreparation.ready(payment.getId(), message.shippingAddress());
    }

    // TX2 - 승인 결과 반영과 주문 생성을 한 트랜잭션으로 묶는다.
    // 둘을 나누면 "결제는 승인됐는데 주문만 없는" 상태가 정상 흐름에서도 생길 수 있어 한 단위로 처리한다
    @Transactional
    public Order completeApproval(Long paymentId, PgApproveResult result, String shippingAddress) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        payment.approve(result.pgTransactionId(), result.approvedAt());

        try {
            return orderRepository.save(Order.paid(
                    payment.getId(),
                    payment.getGroupBuyParticipantId(),
                    payment.getMemberId(),
                    shippingAddress,
                    payment.getAmount()));
        } catch (RuntimeException e) {
            // 커밋 실패와 구분해 실패 로그에 남기기 위해 감싼다 (트랜잭션은 어느 쪽이든 롤백된다)
            throw new OrderCreationException("주문 생성 실패 - paymentId=" + paymentId, e);
        }
    }

    // PG 승인이 거절됐을 때 - 결제는 이뤄지지 않았으므로 상태만 FAILED로 남긴다
    @Transactional
    public void markFailed(Long paymentId) {
        paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND))
                .fail();
    }
}
