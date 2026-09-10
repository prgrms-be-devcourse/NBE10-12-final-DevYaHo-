package com.wellbuying.domain.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.payment.entity.PaymentFailureType;
import com.wellbuying.domain.payment.event.GroupBuyCompletedMessage;
import com.wellbuying.domain.payment.event.PaymentEventContext;
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
    private static final String ORDER_ID = "gb-11111111-2222-3333-4444-555555555555";
    // message의 groupBuyId=1, producerId=3 (아래 setUp의 생성자 인자 순서와 일치)
    private static final PaymentEventContext EVENT_CONTEXT = new PaymentEventContext(1L, 3L);

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
                .thenReturn(PaymentPreparation.ready(PAYMENT_ID, ORDER_ID));
    }

    private PgApproveResult givenApproved() {
        when(billingKeyProvider.findBillingKey(MEMBER_ID))
                .thenReturn(Optional.of(new BillingCredential("bk_test", "cust_test")));
        PgApproveResult result = new PgApproveResult(PG_TRANSACTION_ID, LocalDateTime.now());
        when(paymentGateway.approve(any(PgApproveCommand.class))).thenReturn(result);
        return result;
    }

    @Test
    @DisplayName("정상 흐름 - 승인 결과 반영을 completeApproval 트랜잭션에 위임한다 (완료 이벤트 발행도 그 안에서 일어난다)")
    void 정상_흐름() {
        givenPrepared();
        PgApproveResult result = givenApproved();

        paymentProcessor.process(message);

        verify(paymentTransactionService).completeApproval(PAYMENT_ID, ORDER_ID, EVENT_CONTEXT, result);
        verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    @DisplayName("이미 처리한 이벤트면 결제를 다시 진행하지 않는다")
    void 멱등성_중복_수신() {
        when(paymentConsumedEventRepository.existsByEventId(EVENT_ID)).thenReturn(true);

        paymentProcessor.process(message);

        verifyNoInteractions(paymentTransactionService);
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
    }

    @Test
    @DisplayName("이벤트에 배송지가 없으면 PG 승인을 시도하지 않는다 - prepare 트랜잭션 안에서 실패 이벤트까지 기록된다")
    void 배송지_없음() {
        when(paymentConsumedEventRepository.existsByEventId(EVENT_ID)).thenReturn(false);
        when(paymentGateway.provider()).thenReturn("TOSS");
        when(paymentTransactionService.prepare(message, "TOSS"))
                .thenReturn(PaymentPreparation.failed(PAYMENT_ID, "이벤트에 배송지가 없음"));

        paymentProcessor.process(message);

        verify(paymentGateway, never()).approve(any());
        verify(paymentTransactionService, never()).markFailed(any(), any(), any(), any());
        verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    @DisplayName("빌링키가 없으면 승인을 시도하지 않고 결제를 실패 처리한다")
    void 빌링키_없음() {
        givenPrepared();
        when(billingKeyProvider.findBillingKey(MEMBER_ID)).thenReturn(Optional.empty());

        paymentProcessor.process(message);

        verify(paymentGateway, never()).approve(any());
        verify(paymentTransactionService).markFailed(PAYMENT_ID, ORDER_ID, EVENT_CONTEXT, "등록된 빌링키가 없음");
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

        verify(paymentTransactionService).markFailed(PAYMENT_ID, ORDER_ID, EVENT_CONTEXT, "카드 한도 초과");
        // 승인이 안 됐으므로 수동 처리 대상이 아니다
        verifyNoInteractions(paymentFailureRecorder);
    }

    @Test
    @DisplayName("승인 후 주문 반영이 깨지면 ORDER_CREATE_FAILED로 기록한다")
    void 승인_후_주문_반영_실패() {
        givenPrepared();
        PgApproveResult result = givenApproved();
        doThrow(new OrderCreationException("승인 전에 만들어 둔 주문을 찾지 못함"))
                .when(paymentTransactionService).completeApproval(PAYMENT_ID, ORDER_ID, EVENT_CONTEXT, result);

        paymentProcessor.process(message);

        verify(paymentFailureRecorder).record(eq(PaymentFailureType.ORDER_CREATE_FAILED), eq(message), eq(PAYMENT_ID),
                eq(PG_TRANSACTION_ID), any(Throwable.class));
    }

    @Test
    @DisplayName("승인 후 커밋이 깨지면 APPROVE_RESULT_PERSIST_FAILED로 기록한다")
    void 승인_후_커밋_실패() {
        givenPrepared();
        PgApproveResult result = givenApproved();
        doThrow(new DataIntegrityViolationException("commit failed"))
                .when(paymentTransactionService).completeApproval(PAYMENT_ID, ORDER_ID, EVENT_CONTEXT, result);

        paymentProcessor.process(message);

        verify(paymentFailureRecorder).record(eq(PaymentFailureType.APPROVE_RESULT_PERSIST_FAILED), eq(message),
                eq(PAYMENT_ID), eq(PG_TRANSACTION_ID), any(Throwable.class));
    }
}
