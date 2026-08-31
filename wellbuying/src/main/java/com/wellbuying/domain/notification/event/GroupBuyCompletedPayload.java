package com.wellbuying.domain.notification.event;

// groupbuy-events 토픽의 GroupBuyCompleted 이벤트 중, 알림 생성에 필요한 필드만 담은 소비자 측 계약.
// 발행 측 레코드(domain.groupbuy.event.GroupBuyCompletedEvent)를 직접 참조하지 않고 분리해 도메인 결합을 피한다.
public record GroupBuyCompletedPayload(
        Long groupBuyId,
        Long productId,
        Long memberId
) {
}
