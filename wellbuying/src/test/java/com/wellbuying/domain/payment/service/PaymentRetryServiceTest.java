package com.wellbuying.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.event.PaymentEventContext;
import com.wellbuying.domain.payment.gateway.BillingCredential;
import com.wellbuying.domain.payment.gateway.BillingKeyProvider;
import com.wellbuying.domain.payment.gateway.PaymentGateway;
import com.wellbuying.domain.payment.gateway.PgApprovalException;
import com.wellbuying.domain.payment.gateway.PgApproveCommand;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentRetryServiceTest {

    private static final Long MEMBER_ID = 100L;
    private static final Long PART_ID = 7L;
    private static final Long GROUP_BUY_ID = 3L;
    private static final Long PRODUCER_ID = 200L;
    private static final Long PAYMENT_ID = 55L;
    private static final String FAILED_ORDER_ID = "gb-failed-1111";
    private static final String NEW_ORDER_ID = "gb-retry-2222";
    private static final String ADDRESS = "서울시 강남구 123";
    private static final int GRACE_DAYS = 3;
    // groupBuy()의 id·producerId에서 만들어지는 값 (PaymentRetryService가 트랜잭션 메서드에 넘긴다)
    private static final PaymentEventContext EVENT_CONTEXT = new PaymentEventContext(GROUP_BUY_ID, PRODUCER_ID);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;
    @Mock
    private GroupBuyRepository groupBuyRepository;
    @Mock
    private PaymentTransactionService paymentTransactionService;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private BillingKeyProvider billingKeyProvider;

    private PaymentRetryService paymentRetryService;
    private Order failedOrder;

    @BeforeEach
    void setUp() {
        // @Value(repayment.grace-period-days) 주입 때문에 @InjectMocks 대신 수동 생성한다
        paymentRetryService = new PaymentRetryService(orderRepository, groupBuyPartRepository, groupBuyRepository,
                paymentTransactionService, paymentGateway, billingKeyProvider, GRACE_DAYS);
        failedOrder = Order.pending(PAYMENT_ID, PART_ID, MEMBER_ID, ADDRESS, 30_000);
        failedOrder.markPaymentFailed();
    }

    private GroupBuyPart part() {
        GroupBuyPart part = GroupBuyPart.confirm(GROUP_BUY_ID, MEMBER_ID, 3);
        ReflectionTestUtils.setField(part, "id", PART_ID);
        return part;
    }

    // 기본 groupBuy는 finalized_at이 null - 유예가 시작도 안 한 상태(가드 통과)
    private GroupBuy groupBuy() {
        return groupBuyFinalizedAt(null);
    }

    private GroupBuy groupBuyFinalizedAt(LocalDateTime finalizedAt) {
        GroupBuy groupBuy = GroupBuy.create(1L, PRODUCER_ID, "제철 사과 공동구매",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), 1, 100);
        ReflectionTestUtils.setField(groupBuy, "id", GROUP_BUY_ID);
        if (finalizedAt != null) {
            ReflectionTestUtils.setField(groupBuy, "finalizedAt", finalizedAt);
        }
        return groupBuy;
    }

    private void givenOwnedFailedOrder() {
        givenOwnedFailedOrder(groupBuy());
    }

    private void givenOwnedFailedOrder(GroupBuy groupBuy) {
        when(orderRepository.findByOrderIdAndMemberId(FAILED_ORDER_ID, MEMBER_ID))
                .thenReturn(Optional.of(failedOrder));
        when(groupBuyPartRepository.findById(PART_ID)).thenReturn(Optional.of(part()));
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
        when(billingKeyProvider.findBillingKey(MEMBER_ID))
                .thenReturn(Optional.of(new BillingCredential("bk_test", "cust_test")));
        when(paymentGateway.provider()).thenReturn("TOSS");
        when(paymentTransactionService.prepareRetry(eq(failedOrder), eq("TOSS"), anyString()))
                .thenReturn(PaymentPreparation.ready(PAYMENT_ID, NEW_ORDER_ID));
    }

    @Test
    @DisplayName("정상 흐름 - 승인 결과 반영을 completeApproval 트랜잭션에 위임하고 새 주문 id를 돌려준다 (완료 이벤트 발행도 그 안에서 일어난다)")
    void 정상_흐름() {
        givenOwnedFailedOrder();
        PgApproveResult result = new PgApproveResult("toss-tx-retry", LocalDateTime.now());
        when(paymentGateway.approve(any(PgApproveCommand.class))).thenReturn(result);
        Order newOrder = Order.pending(PAYMENT_ID, PART_ID, MEMBER_ID, ADDRESS, 30_000);
        newOrder.markPaid();
        when(paymentTransactionService.completeApproval(PAYMENT_ID, NEW_ORDER_ID, EVENT_CONTEXT, result))
                .thenReturn(newOrder);

        String orderId = paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID);

        assertThat(orderId).isEqualTo(newOrder.getOrderId());
        verify(paymentTransactionService).completeApproval(PAYMENT_ID, NEW_ORDER_ID, EVENT_CONTEXT, result);
    }

    @Test
    @DisplayName("PAYMENT_FAILED 상태가 아닌 주문은 재시도할 수 없다")
    void 실패하지_않은_주문은_재시도_불가() {
        Order paidOrder = Order.pending(PAYMENT_ID, PART_ID, MEMBER_ID, ADDRESS, 30_000);
        paidOrder.markPaid();
        when(orderRepository.findByOrderIdAndMemberId(FAILED_ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(paidOrder));

        assertThatThrownBy(() -> paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_RETRYABLE);
        verify(paymentGateway, never()).approve(any());
    }

    @Test
    @DisplayName("본인 소유가 아니면 ORDER_NOT_FOUND")
    void 본인_소유가_아니면_ORDER_NOT_FOUND() {
        when(orderRepository.findByOrderIdAndMemberId(FAILED_ORDER_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("빌링키가 없으면 승인을 시도하지 않고 BILLING_KEY_NOT_FOUND를 던진다")
    void 빌링키_없음() {
        when(orderRepository.findByOrderIdAndMemberId(FAILED_ORDER_ID, MEMBER_ID))
                .thenReturn(Optional.of(failedOrder));
        when(groupBuyPartRepository.findById(PART_ID)).thenReturn(Optional.of(part()));
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy()));
        when(billingKeyProvider.findBillingKey(MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BILLING_KEY_NOT_FOUND);
        verify(paymentGateway, never()).approve(any());
        verify(paymentTransactionService, never()).prepareRetry(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("PG가 다시 거절하면 새 주문을 PAYMENT_FAILED로 남기고 실패 이벤트를 발행한다")
    void PG_승인_재거절() {
        givenOwnedFailedOrder();
        when(paymentGateway.approve(any(PgApproveCommand.class)))
                .thenThrow(new PgApprovalException("카드 한도 초과"));

        String orderId = paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID);

        assertThat(orderId).isEqualTo(NEW_ORDER_ID);
        verify(paymentTransactionService).markFailed(PAYMENT_ID, NEW_ORDER_ID, EVENT_CONTEXT, "카드 한도 초과");
    }

    @Test
    @DisplayName("재시도는 매번 새로운 멱등키로 PG를 호출한다 - 같은 키를 재사용하면 PG가 실패했던 예전 응답을 그대로 돌려줄 수 있다")
    void 매번_새로운_멱등키() {
        givenOwnedFailedOrder();
        when(paymentGateway.approve(any(PgApproveCommand.class)))
                .thenThrow(new PgApprovalException("카드 한도 초과"));
        ArgumentCaptor<String> idempotencyKeyCaptor = ArgumentCaptor.forClass(String.class);

        paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID);
        paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID);

        verify(paymentTransactionService, Mockito.times(2))
                .prepareRetry(eq(failedOrder), eq("TOSS"), idempotencyKeyCaptor.capture());
        assertThat(idempotencyKeyCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("finalized_at + 유효기간이 지났으면 승인 시도 없이 REPAYMENT_WINDOW_CLOSED")
    void 재결제_유효기간_만료() {
        when(orderRepository.findByOrderIdAndMemberId(FAILED_ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(failedOrder));
        when(groupBuyPartRepository.findById(PART_ID)).thenReturn(Optional.of(part()));
        when(groupBuyRepository.findById(GROUP_BUY_ID))
                .thenReturn(Optional.of(groupBuyFinalizedAt(LocalDateTime.now().minusDays(GRACE_DAYS + 1))));

        assertThatThrownBy(() -> paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REPAYMENT_WINDOW_CLOSED);
        verify(billingKeyProvider, never()).findBillingKey(any());
        verify(paymentGateway, never()).approve(any());
        verify(paymentTransactionService, never()).prepareRetry(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("finalized_at + 유효기간이 아직 안 지났으면 정상적으로 재결제한다")
    void 재결제_유효기간_내() {
        givenOwnedFailedOrder(groupBuyFinalizedAt(LocalDateTime.now().minusDays(1)));
        PgApproveResult result = new PgApproveResult("toss-tx-retry", LocalDateTime.now());
        when(paymentGateway.approve(any(PgApproveCommand.class))).thenReturn(result);
        Order newOrder = Order.pending(PAYMENT_ID, PART_ID, MEMBER_ID, ADDRESS, 30_000);
        newOrder.markPaid();
        when(paymentTransactionService.completeApproval(PAYMENT_ID, NEW_ORDER_ID, EVENT_CONTEXT, result))
                .thenReturn(newOrder);

        String orderId = paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID);

        assertThat(orderId).isEqualTo(newOrder.getOrderId());
        verify(paymentGateway).approve(any(PgApproveCommand.class));
    }

    @Test
    @DisplayName("finalized_at이 아직 null이면(성사 후처리 전) 재결제를 허용한다")
    void finalized_at_null이면_허용() {
        givenOwnedFailedOrder(groupBuyFinalizedAt(null));
        when(paymentGateway.approve(any(PgApproveCommand.class)))
                .thenThrow(new PgApprovalException("카드 한도 초과"));

        paymentRetryService.retry(MEMBER_ID, FAILED_ORDER_ID);

        verify(paymentGateway).approve(any(PgApproveCommand.class));
    }
}
