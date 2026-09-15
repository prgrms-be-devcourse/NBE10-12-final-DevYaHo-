package com.wellbuying.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.entity.OrderStatus;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.entity.PaymentStatus;
import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import com.wellbuying.domain.payment.event.PaymentEventContext;
import com.wellbuying.domain.payment.event.PaymentEventType;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.domain.payment.repository.PaymentConsumedEventRepository;
import com.wellbuying.domain.payment.repository.PaymentEventOutboxRepository;
import com.wellbuying.domain.payment.repository.PaymentRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// PaymentTransactionService는 실제 트랜잭션 안에서 벌어지는 상태 전이·outbox 기록을 검증해야 하는
// 클래스라 Mockito로는 본질을 확인할 수 없다 (PaymentProcessorTest/PaymentRetryServiceTest는 이 클래스를
// 전부 @Mock으로 대체한다). 실제 DB(Testcontainers Postgres)로 직접 실행해 검증한다
@Transactional
class PaymentTransactionServiceTest extends AbstractIntegrationTest {

    private static final String ADDRESS = "서울시 강남구 테헤란로 123";
    private static final String PG_PROVIDER = "TOSS";

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentConsumedEventRepository paymentConsumedEventRepository;

    @Autowired
    private PaymentEventOutboxRepository paymentEventOutboxRepository;

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @Autowired
    private GroupBuyPartRepository groupBuyPartRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;
    private Long partId;
    private Long groupBuyId;

    @BeforeEach
    void setUp() {
        Long producerId = memberRepository
                .save(Member.signUp("producer-" + System.nanoTime() + "@example.com", "encoded-password", "생산자"))
                .getId();
        memberId = memberRepository
                .save(Member.signUp("buyer-" + System.nanoTime() + "@example.com", "encoded-password", "구매자"))
                .getId();
        GroupBuy groupBuy = GroupBuy.create(1L, producerId, "제철 사과 공동구매",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), 1, 100);
        groupBuyId = groupBuyRepository.save(groupBuy).getId();
        partId = groupBuyPartRepository.save(GroupBuyPart.confirm(groupBuyId, memberId, 2)).getId();
    }

    private GroupBuyCompletedMessage message(String shippingAddress) {
        return new GroupBuyCompletedMessage(GroupBuyCompletedMessage.TYPE, groupBuyId, 1L, 200L, partId, memberId, 2,
                5_000, shippingAddress, LocalDateTime.now());
    }

    private PaymentEventContext eventContext() {
        return new PaymentEventContext(groupBuyId, 200L);
    }

    @Test
    @DisplayName("prepare: 정상 흐름이면 처리 이력/READY 결제/PENDING 주문이 모두 남는다")
    void prepare_정상() {
        GroupBuyCompletedMessage message = message(ADDRESS);

        PaymentPreparation preparation = paymentTransactionService.prepare(message, PG_PROVIDER);

        assertThat(preparation.failed()).isFalse();
        assertThat(paymentConsumedEventRepository.existsByEventId(message.eventId())).isTrue();

        Payment payment = paymentRepository.findById(preparation.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getAmount()).isEqualTo(10_000);

        Order order = orderRepository.findById(preparation.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentId()).isEqualTo(payment.getId());
        assertThat(order.getShippingAddress()).isEqualTo(ADDRESS);
    }

    @Test
    @DisplayName("prepare: 배송지가 없으면 주문은 만들지 않고 결제를 FAILED로 남기며 실패 이벤트를 outbox에 기록한다")
    void prepare_배송지_없음() {
        GroupBuyCompletedMessage message = message(null);

        PaymentPreparation preparation = paymentTransactionService.prepare(message, PG_PROVIDER);

        assertThat(preparation.failed()).isTrue();
        assertThat(preparation.orderId()).isNull();

        Payment payment = paymentRepository.findById(preparation.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(orderRepository.findAll()).isEmpty();

        assertThat(paymentEventOutboxRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(outbox -> {
                    assertThat(outbox.getEventType()).isEqualTo(PaymentEventType.PAYMENT_FAILED.code());
                    assertThat(outbox.getGroupBuyId()).isEqualTo(groupBuyId);
                });
    }

    @Test
    @DisplayName("completeApproval: 정상이면 결제 APPROVED, 주문 PAID, 완료 이벤트가 outbox에 남는다")
    void completeApproval_정상() {
        PaymentPreparation preparation = paymentTransactionService.prepare(message(ADDRESS), PG_PROVIDER);
        PgApproveResult result = new PgApproveResult("pg_tx_1", LocalDateTime.now());

        Order order = paymentTransactionService.completeApproval(preparation.paymentId(), preparation.orderId(),
                eventContext(), result);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        Payment payment = paymentRepository.findById(preparation.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTransactionId()).isEqualTo("pg_tx_1");

        assertThat(paymentEventOutboxRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(outbox -> assertThat(outbox.getEventType()).isEqualTo(PaymentEventType.PAYMENT_COMPLETED.code()));
    }

    @Test
    @DisplayName("completeApproval: TX1에서 만든 주문을 찾지 못하면 OrderCreationException이 발생한다")
    void completeApproval_주문_없음() {
        PaymentPreparation preparation = paymentTransactionService.prepare(message(ADDRESS), PG_PROVIDER);
        PgApproveResult result = new PgApproveResult("pg_tx_2", LocalDateTime.now());
        String missingOrderId = "gb-does-not-exist";

        assertThatThrownBy(() -> paymentTransactionService.completeApproval(preparation.paymentId(), missingOrderId,
                eventContext(), result))
                .isInstanceOf(OrderCreationException.class);
    }

    @Test
    @DisplayName("markFailed: 결제/주문을 FAILED로 전이시키고 실패 이벤트를 outbox에 남긴다")
    void markFailed_정상() {
        PaymentPreparation preparation = paymentTransactionService.prepare(message(ADDRESS), PG_PROVIDER);

        paymentTransactionService.markFailed(preparation.paymentId(), preparation.orderId(), eventContext(),
                "카드 한도 초과");

        Payment payment = paymentRepository.findById(preparation.paymentId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        Order order = orderRepository.findById(preparation.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

        assertThat(paymentEventOutboxRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(outbox -> assertThat(outbox.getEventType()).isEqualTo(PaymentEventType.PAYMENT_FAILED.code()));
    }

    @Test
    @DisplayName("prepareRetry: 실패했던 원래 주문은 그대로 두고 새 Payment/Order 쌍을 만든다")
    void prepareRetry_정상() {
        // 부분 유니크 인덱스(uk_payment_group_buy_participant_id)는 status<>'FAILED'에만 걸리므로,
        // 새 Payment를 INSERT하기 전에 원래 건의 FAILED 전이가 먼저 DB에 반영돼 있어야 한다.
        // Hibernate는 같은 플러시에서 INSERT를 UPDATE보다 먼저 실행하므로 명시적으로 flush해 순서를 보장한다
        Payment originalPayment = paymentRepository.save(Payment.ready(partId, memberId, 10_000, PG_PROVIDER, "old-key"));
        originalPayment.fail();
        paymentRepository.saveAndFlush(originalPayment);
        Order failedOrder = orderRepository.save(
                Order.pending(originalPayment.getId(), partId, memberId, ADDRESS, 10_000));
        failedOrder.markPaymentFailed();
        orderRepository.saveAndFlush(failedOrder);

        PaymentPreparation preparation = paymentTransactionService.prepareRetry(failedOrder, PG_PROVIDER, "retry-key-1");

        assertThat(preparation.failed()).isFalse();
        assertThat(preparation.orderId()).isNotEqualTo(failedOrder.getOrderId());

        Payment newPayment = paymentRepository.findById(preparation.paymentId()).orElseThrow();
        assertThat(newPayment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(newPayment.getIdempotencyKey()).isEqualTo("retry-key-1");
        assertThat(newPayment.getAmount()).isEqualTo(failedOrder.getTotalPrice());

        Order newOrder = orderRepository.findById(preparation.orderId()).orElseThrow();
        assertThat(newOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(newOrder.getShippingAddress()).isEqualTo(failedOrder.getShippingAddress());

        // 실패했던 원래 주문은 손대지 않고 이력으로 남아있어야 한다
        Order untouched = orderRepository.findById(failedOrder.getOrderId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }
}
