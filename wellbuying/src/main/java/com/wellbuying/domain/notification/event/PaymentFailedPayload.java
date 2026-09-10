package com.wellbuying.domain.notification.event;

// payment-events 토픽의 PaymentFailed 이벤트 중, 알림 생성에 필요한 필드만 담은 소비자 측 계약.
// 발행 측 레코드(domain.payment.event.PaymentFailedEvent)를 직접 참조하지 않고 분리해 도메인 결합을 피한다.
// 이 이벤트에는 productId가 없다 - notification 테이블의 product_id 컬럼은 nullable이라 null로 저장한다.
public record PaymentFailedPayload(
        Long groupBuyId,
        Long memberId
) {
}
