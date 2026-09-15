package com.wellbuying.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.entity.Payment;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// UNCONFIRMED 정리 배치의 분기(선점 실패/승인 확인/조회 실패/미승인 후 자동 재시도)를 검증한다.
// PG 호출은 PaymentGateway를 mock으로 대체하므로 실제 HTTP는 나가지 않는다 (09-pg-timeout-retry.md)
@ExtendWith(MockitoExtension.class)
class PaymentUnconfirmedReconciliationJobTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long PART_ID = 7L;
    private static final Long MEMBER_ID = 100L;
    private static final Long GROUP_BUY_ID = 3L;
    private static final Long PRODUCER_ID = 200L;
    private static final String ORDER_ID = "gb-unconfirmed-1111";
    private static final PaymentEventContext EVENT_CONTEXT = new PaymentEventContext(GROUP_BUY_ID, PRODUCER_ID);

    @Mock
    private PaymentRepository paymentRepository;
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
    @Mock
    private PaymentFailureLogRepository paymentFailureLogRepository;

    private PaymentUnconfirmedReconciliationJob job;
    private Payment unconfirmedPayment;
    private Order order;

    @BeforeEach
    void setUp() {
        job = new PaymentUnconfirmedReconciliationJob(paymentRepository, orderRepository, groupBuyPartRepository,
                groupBuyRepository, paymentTransactionService, paymentGateway, billingKeyProvider,
                paymentFailureLogRepository);

        unconfirmedPayment = Payment.ready(PART_ID, MEMBER_ID, 30_000, "TOSS", "GroupBuyCompleted:7");
        ReflectionTestUtils.setField(unconfirmedPayment, "id", PAYMENT_ID);
        order = Order.pending(PAYMENT_ID, PART_ID, MEMBER_ID, "서울시 강남구", 30_000);
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
    }

    private void givenTargetPayment() {
        when(paymentRepository.findByStatus(PaymentStatus.UNCONFIRMED)).thenReturn(List.of(unconfirmedPayment));
    }

    private void givenClaimed() {
        when(paymentRepository.claimForReconciliation(PAYMENT_ID)).thenReturn(1);
        when(orderRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(order));
    }

    private void givenGroupBuyContext() {
        GroupBuyPart part = GroupBuyPart.confirm(GROUP_BUY_ID, MEMBER_ID, 2);
        ReflectionTestUtils.setField(part, "id", PART_ID);
        GroupBuy groupBuy = GroupBuy.create(1L, PRODUCER_ID, "제철 사과 공동구매",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), 1, 100);
        ReflectionTestUtils.setField(groupBuy, "id", GROUP_BUY_ID);
        when(groupBuyPartRepository.findById(PART_ID)).thenReturn(Optional.of(part));
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
    }

    @Test
    @DisplayName("선점(claim)에 실패하면(다른 인스턴스가 이미 처리 중) PG를 호출하지 않는다")
    void 선점_실패시_스킵() {
        givenTargetPayment();
        when(paymentRepository.claimForReconciliation(PAYMENT_ID)).thenReturn(0);

        job.reconcile();

        verify(paymentGateway, never()).inquire(any());
        verify(orderRepository, never()).findByPaymentId(any());
    }

    @Test
    @DisplayName("결제조회 결과가 DONE이면 승인 재시도 없이 바로 completeApproval하고 실패 로그를 마감한다")
    void 결제조회_DONE이면_바로_확정() {
        givenTargetPayment();
        givenClaimed();
        givenGroupBuyContext();
        PgInquiryResult inquiry = new PgInquiryResult("DONE", "pk_abc", LocalDateTime.now());
        when(paymentGateway.inquire(ORDER_ID)).thenReturn(inquiry);
        when(paymentFailureLogRepository.findByPaymentIdAndFailureTypeAndResolvedFalse(PAYMENT_ID,
                PaymentFailureType.APPROVAL_UNCONFIRMED_AFTER_TIMEOUT)).thenReturn(List.of());

        job.reconcile();

        verify(paymentTransactionService).completeApproval(eq(PAYMENT_ID), eq(ORDER_ID), eq(EVENT_CONTEXT),
                eq(inquiry.toApproveResult()));
        verify(paymentGateway, never()).approve(any());
        verify(paymentRepository, never()).releaseReconciliationClaim(any());
    }

    @Test
    @DisplayName("결제조회 호출 자체가 실패하면 선점을 풀어 다음 주기에 다시 보게 한다")
    void 결제조회_실패시_선점_해제() {
        givenTargetPayment();
        givenClaimed();
        when(paymentGateway.inquire(ORDER_ID)).thenThrow(new PgInquiryException("네트워크 오류", null));

        job.reconcile();

        verify(paymentRepository).releaseReconciliationClaim(PAYMENT_ID);
        verify(paymentGateway, never()).approve(any());
        verify(paymentTransactionService, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    @DisplayName("미승인 확인되면 새 Idempotency-Key로 자동 재시도하고, 성공하면 completeApproval한다")
    void 미승인_확인후_자동재시도_성공() {
        givenTargetPayment();
        givenClaimed();
        givenGroupBuyContext();
        when(paymentGateway.inquire(ORDER_ID)).thenReturn(new PgInquiryResult("NOT_FOUND_PAYMENT", null, null));
        when(billingKeyProvider.findBillingKey(MEMBER_ID))
                .thenReturn(Optional.of(new BillingCredential("bk_test", "cust_test")));
        PgApproveResult approveResult = new PgApproveResult("pk_retry", LocalDateTime.now());
        when(paymentGateway.approve(any(PgApproveCommand.class))).thenReturn(approveResult);
        when(paymentFailureLogRepository.findByPaymentIdAndFailureTypeAndResolvedFalse(PAYMENT_ID,
                PaymentFailureType.APPROVAL_UNCONFIRMED_AFTER_TIMEOUT)).thenReturn(List.of());

        job.reconcile();

        verify(paymentTransactionService).completeApproval(PAYMENT_ID, ORDER_ID, EVENT_CONTEXT, approveResult);
        verify(paymentRepository, never()).releaseReconciliationClaim(any());
    }

    @Test
    @DisplayName("미승인 확인 후 자동 재시도마저 거절되면 그 자리에서 FAILED로 확정한다 (더 반복하지 않음)")
    void 미승인_확인후_자동재시도_실패() {
        givenTargetPayment();
        givenClaimed();
        givenGroupBuyContext();
        when(paymentGateway.inquire(ORDER_ID)).thenReturn(new PgInquiryResult("ABORTED", null, null));
        when(billingKeyProvider.findBillingKey(MEMBER_ID))
                .thenReturn(Optional.of(new BillingCredential("bk_test", "cust_test")));
        when(paymentGateway.approve(any(PgApproveCommand.class))).thenThrow(new PgApprovalException("카드 한도 초과"));
        when(paymentFailureLogRepository.findByPaymentIdAndFailureTypeAndResolvedFalse(PAYMENT_ID,
                PaymentFailureType.APPROVAL_UNCONFIRMED_AFTER_TIMEOUT)).thenReturn(List.of());

        job.reconcile();

        verify(paymentTransactionService).markFailed(PAYMENT_ID, ORDER_ID, EVENT_CONTEXT, "카드 한도 초과");
        verify(paymentTransactionService, never()).completeApproval(any(), any(), any(), any());
        verify(paymentRepository, never()).releaseReconciliationClaim(any());
    }

    @Test
    @DisplayName("결제조회 결과가 진행 중 상태면 확정하지 않고 선점을 풀어 다음 주기에 재확인한다")
    void 진행중_상태면_다음주기_재확인() {
        givenTargetPayment();
        givenClaimed();
        when(paymentGateway.inquire(ORDER_ID)).thenReturn(new PgInquiryResult("IN_PROGRESS", null, null));

        job.reconcile();

        verify(paymentGateway, never()).approve(any());
        verify(paymentRepository).releaseReconciliationClaim(PAYMENT_ID);
    }
}
