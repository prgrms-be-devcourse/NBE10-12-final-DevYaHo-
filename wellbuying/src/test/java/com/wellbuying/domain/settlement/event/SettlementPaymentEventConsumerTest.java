package com.wellbuying.domain.settlement.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.settlement.service.SettlementAccrualService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

// eventType으로 PaymentCompleted만 골라 SettlementAccrualService로 위임하는지, 그리고 깨진 메시지를
// 예외 없이 건너뛰는지 검증한다 - 실제 적립 로직은 SettlementAccrualServiceTest가 다룬다
class SettlementPaymentEventConsumerTest {

    private final SettlementAccrualService accrualService = mock(SettlementAccrualService.class);
    private final SettlementPaymentEventConsumer consumer =
            new SettlementPaymentEventConsumer(new ObjectMapper(), accrualService);

    @Test
    void PaymentCompleted_이벤트는_필요한_필드를_담아_적립_서비스로_위임한다() {
        String payload = """
                {"eventType":"PaymentCompleted","paymentId":1,"orderId":"gb-o-1","groupBuyId":42,
                "groupBuyParticipantId":50,"memberId":100,"producerId":5,"amount":10000,
                "pgTransactionId":"tx-1","occurredAt":"2026-01-01T00:00:00"}
                """;

        consumer.onMessage(payload);

        ArgumentCaptor<PaymentCompletedMessage> captor = ArgumentCaptor.forClass(PaymentCompletedMessage.class);
        verify(accrualService, times(1)).accrue(captor.capture());
        PaymentCompletedMessage message = captor.getValue();
        assertThat(message.groupBuyId()).isEqualTo(42L);
        assertThat(message.groupBuyParticipantId()).isEqualTo(50L);
        assertThat(message.memberId()).isEqualTo(100L);
        assertThat(message.producerId()).isEqualTo(5L);
        assertThat(message.amount()).isEqualTo(10000);
        assertThat(message.occurredAt()).isEqualTo(LocalDateTime.parse("2026-01-01T00:00:00"));
    }

    @Test
    void PaymentFailed_이벤트는_정산_대상이_아니므로_무시한다() {
        String payload = """
                {"eventType":"PaymentFailed","paymentId":1,"groupBuyId":42,"groupBuyParticipantId":50,
                "memberId":100,"amount":10000,"reason":"등록된 빌링키가 없음","occurredAt":"2026-01-01T00:00:00"}
                """;

        consumer.onMessage(payload);

        verify(accrualService, never()).accrue(any());
    }

    @Test
    void 정산_대상이_아닌_이벤트_타입은_무시한다() {
        consumer.onMessage("""
                {"eventType":"SomethingElse","groupBuyId":42,"amount":10000}
                """);

        verify(accrualService, never()).accrue(any());
    }

    // poison message는 재시도해도 성공할 수 없으므로 예외를 밖으로 던져 컨슈머를 무한 재시도에 빠뜨리는 대신
    // 여기서 잡아 소비를 끝내야 한다
    @Test
    void 형식이_깨진_페이로드는_예외를_던지지_않고_건너뛴다() {
        consumer.onMessage("{이것은-유효한-JSON이-아님");

        verify(accrualService, never()).accrue(any());
    }
}
