package com.wellbuying.domain.settlement.event;

import java.time.LocalDateTime;

// payment-events 토픽의 PaymentCompleted 이벤트 중 정산 적립에 필요한 필드만 담은 소비자 측 계약.
// 발행 측 레코드(domain.payment.event.PaymentCompletedEvent)를 직접 참조하지 않고 분리해 도메인 결합을 피한다
// (notification 도메인의 PaymentCompletedPayload와 같은 방식).
public record PaymentCompletedMessage(
        Long groupBuyId,
        Long groupBuyParticipantId,
        Long memberId,
        Long producerId,
        int amount,
        LocalDateTime occurredAt
) {
}
