package com.wellbuying.domain.payment.event;

// 결제 이벤트(payment-events)에 실어야 하지만 Payment/Order 엔티티만으로는 알 수 없는 값.
// 카프카 성사 이벤트 경로(PaymentProcessor)는 GroupBuyCompletedMessage에서, 수동 재시도 경로
// (PaymentRetryService)는 group_buy 엔티티에서 채워 PaymentTransactionService에 넘긴다.
// 이 값을 파라미터로 받아야 두 경로가 같은 트랜잭션 메서드(completeApproval/markFailed)를 써서
// 발행까지 원자적으로 처리할 수 있다.
public record PaymentEventContext(Long groupBuyId, Long producerId) {
}
