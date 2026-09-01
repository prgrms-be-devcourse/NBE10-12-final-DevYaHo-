package com.wellbuying.domain.notification.event;

// groupbuy-events 토픽의 GroupBuyFailed 이벤트 중, 알림 생성에 필요한 필드만 담은 소비자 측 계약.
// 이 이벤트에는 참여자별 memberId가 없으므로(공동구매당 1건만 발행), 소비자가 groupBuyId로 참여자 목록을 직접 조회한다.
public record GroupBuyFailedPayload(
        Long groupBuyId,
        Long productId
) {
}
