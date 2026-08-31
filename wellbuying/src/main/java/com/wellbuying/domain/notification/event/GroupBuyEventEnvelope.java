package com.wellbuying.domain.notification.event;

// groupbuy-events 페이로드에서 분기에 필요한 eventType만 우선 읽어내기 위한 최소 계약.
// 나머지 필드는 무시되고, eventType으로 분기한 뒤 구체 타입(GroupBuyCompletedPayload 등)으로 다시 역직렬화한다.
public record GroupBuyEventEnvelope(String eventType) {
}
