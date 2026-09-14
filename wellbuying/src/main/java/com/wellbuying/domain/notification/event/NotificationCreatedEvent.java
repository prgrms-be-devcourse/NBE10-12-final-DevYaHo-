package com.wellbuying.domain.notification.event;

import com.wellbuying.domain.notification.dto.NotificationResponse;

// Notification 저장 트랜잭션이 커밋된 후에만 SSE로 밀어주기 위한 스프링 내부(인메모리) 이벤트.
// 커밋 전에 push하면, 클라이언트가 push를 받자마자 GET /api/notifications를 다시 호출했을 때
// 아직 안 보이는 순간이 생길 수 있어 AFTER_COMMIT 시점에만 소비되도록 한다.
public record NotificationCreatedEvent(Long memberId, NotificationResponse notification) {
}
