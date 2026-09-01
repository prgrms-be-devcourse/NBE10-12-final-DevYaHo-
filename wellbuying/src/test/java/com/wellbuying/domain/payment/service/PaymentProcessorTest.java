package com.wellbuying.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.payment.entity.Order;
import com.wellbuying.domain.payment.entity.PaymentFailureType;
import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import com.wellbuying.domain.payment.event.PaymentCompletedEvent;
import com.wellbuying.domain.payment.event.PaymentEventPublisher;
import com.wellbuying.domain.payment.event.PaymentFailedEvent;
import com.wellbuying.domain.payment.gateway.BillingCredential;
import com.wellbuying.domain.payment.gateway.BillingKeyProvider;
import com.wellbuying.domain.payment.gateway.PaymentGateway;
import com.wellbuying.domain.payment.gateway.PgApprovalException;
import com.wellbuying.domain.payment.gateway.PgApproveCommand;
import com.wellbuying.domain.payment.gateway.PgApproveResult;
import com.wellbuying.domain.payment.repository.PaymentConsumedEventRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorTest {

    private static final Long PART_ID = 77L;
    private static final Long MEMBER_ID = 5L;
    private static final Long PAYMENT_ID = 100L;
    private static final String EVENT_ID = "GroupBuyCompleted:77";
    private static final String ADDRESS = "서울시 강남구 1 (06000)";
    private static final String PG_TRANSACTION_ID = "pay_abc";

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @Mock
    private PaymentFailureRecorder paymentFailureRecorder;

    @Mock
    private PaymentConsumedEventRepository paymentConsumedEventRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private BillingKeyProvider billingKeyProvider;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private PaymentProcessor paymentProcessor;

    private GroupBuyCompletedMessage message;

    @BeforeEach
    void setUp() {
        // 수량 2 * 단가 5000 = 10000원
        message = new GroupBuyCompletedMessage("GroupBuyCompleted", 1L, 2L, 3L, PART_ID, MEMBER_ID, 2, 5000,
                ADDRESS, LocalDateTime.now());
    }

    // 성사 이벤트를 처음 받아 PG 승인 직전까지 준비된 상태로 만든다
    private void givenPrepared() {
        when(paymentConsumedEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);
        when(paymentGateway.provider()).thenReturn("TOSS");
        when(paymentTransactionService.prepare(message, "TOSS"))
                .thenReturn(PaymentPreparation.ready(PAYMENT_ID, ADDRESS));
    }

    private PgApproveResult givenApproved() {
        when(billingKeyProvider.findBillingKey(MEMBER_ID))
                .thenReturn(Optional.of(new BillingCredential("bk_test", "cust_test")));
        PgApproveResult result = new PgApproveResult(PG_TRANSACTION_ID, LocalDateTime.now());
        when(paymentGateway.approve(any(PgApproveCommand.class))).thenReturn(result);
        return result;
    }

    @Test
    @DisplayName("정상 흐름 - 승인 후 주문이 생성되고 결제 완료 이벤트가 발행된다")
    void 정상_흐름() {
        givenPrepared();
        PgApproveResult result = givenApproved();
        Order order = Order.paid(PAYMENT_ID, PART_ID, MEMBER_ID, ADDRESS, 10000);
        when(paymentTransactionService.completeApproval(PAYMENT_ID, result, ADDRESS)).thenReturn(order);

        paymentProcessor.process(message);

        verify(paymentEventPublisher).publishCompleted(any(PaymentCompletedEvent.class));
        verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    @DisplayName("이미 처리한 이벤트면 결제를 다시 진행하지 않는다")
    void 멱등성_중복_수신() {
        when(paymentConsumedEventRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        paymentProcessor.process(message);

        verifyNoInteractions(paymentTransactionService, paymentEventPublisher);
    }

    @Test
    @DisplayName("동시 중복 수신으로 UNIQUE 제약에 걸리면 조용히 종료한다")
    void 동시_중복_수신() {
        when(paymentConsumedEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);
        when(paymentGateway.provider()).thenReturn("TOSS");
        when(paymentTransactionService.prepare(message, "TOSS"))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        paymentProcessor.process(message);

        verify(paymentGateway, never()).approve(any());
        verifyNoInteractions(paymentEventPublisher);
    }

    @Test
    @DisplayName("이벤트에 배송지가 없으면 PG 승인을 시도하지 않는다 - 돈이 나가기 전에 걸러야 하므로")
    void 배송지_없음() {
        when(paymentConsumedEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);
        when(paymentGateway.provider()).thenReturn("TOSS");
        when(paymentTransactionService.prepare(message, "TOSS"))
                .thenReturn(PaymentPreparation.failed(PAYMENT_ID, "이벤트에 배송지가 없음"));

        paymentProcessor.process(message);

        verify(paymentGateway, never()).approve(any());
        verify(paymentEventPublisher).publishFailed(any(PaymentFailedEvent.class));
        verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    @DisplayName("빌링키가 없으면 승인을 시도하지 않고 결제를 실패 처리한다")
    void 빌링키_없음() {
        givenPrepared();
        when(billingKeyProvider.findBillingKey(MEMBER_ID)).thenReturn(Optional.empty());

        paymentProcessor.process(message);

        verify(paymentGateway, never()).approve(any());
        verify(paymentTransactionService).markFailed(PAYMENT_ID);
        verify(paymentEventPublisher).publishFailed(any(PaymentFailedEvent.class));
    }

    @Test
    @DisplayName("PG가 승인을 거절하면 결제를 FAILED로 남기고 실패 이벤트를 발행한다")
    void PG_승인_거절() {
        givenPrepared();
        when(billingKeyProvider.findBillingKey(MEMBER_ID))
                .thenReturn(Optional.of(new BillingCredential("bk_test", "cust_test")));
        when(paymentGateway.approve(any(PgApproveCommand.class)))
                .thenThrow(new PgApprovalException("카드 한도 초과"));

        paymentProcessor.process(message);

        verify(paymentTransactionService).markFailed(PAYMENT_ID);
        verify(paymentEventPublisher).publishFailed(any(PaymentFailedEvent.class));
        // 승인이 안 됐으므로 수동 처리 대상이 아니다
        verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    @DisplayName("승인 후 주문 생성이 깨지면 ORDER_CREATE_FAILED로 기록하고 완료 이벤트를 발행하지 않는다")
    void 승인_후_주문_생성_실패() {
        givenPrepared();
        PgApproveResult result = givenApproved();
        when(paymentTransactionService.completeApproval(PAYMENT_ID, result, ADDRESS))
                .thenThrow(new OrderCreationException("주문 생성 실패", new RuntimeException()));

        paymentProcessor.process(message);

        verify(paymentFailureRecorder).record(eq(PaymentFailureType.ORDER_CREATE_FAILED), eq(message), eq(PAYMENT_ID),
                eq(PG_TRANSACTION_ID), any(Throwable.class));
        verify(paymentEventPublisher, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("승인 후 커밋이 깨지면 APPROVE_RESULT_PERSIST_FAILED로 기록한다")
    void 승인_후_커밋_실패() {
        givenPrepared();
        PgApproveResult result = givenApproved();
        when(paymentTransactionService.completeApproval(PAYMENT_ID, result, ADDRESS))
                .thenThrow(new DataIntegrityViolationException("commit failed"));

        paymentProcessor.process(message);

        verify(paymentFailureRecorder).record(eq(PaymentFailureType.APPROVE_RESULT_PERSIST_FAILED), eq(message),
                eq(PAYMENT_ID), eq(PG_TRANSACTION_ID), any(Throwable.class));
        verify(paymentEventPublisher, never()).publishCompleted(any());
    }
}
