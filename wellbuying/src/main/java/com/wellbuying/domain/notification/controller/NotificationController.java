package com.wellbuying.domain.notification.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.notification.dto.NotificationResponse;
import com.wellbuying.domain.notification.dto.NotificationUnreadCountResponse;
import com.wellbuying.domain.notification.service.NotificationService;
import com.wellbuying.domain.notification.sse.NotificationSseService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "알림", description = "공동구매 성사/실패 알림 조회 및 읽음 처리")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    public NotificationController(NotificationService notificationService,
            NotificationSseService notificationSseService) {
        this.notificationService = notificationService;
        this.notificationSseService = notificationSseService;
    }

    // 30초 폴링을 대체하는 인메모리 SSE 구독. 서버 1대 전제(단일 JVM 메모리)이므로 스케일아웃 시
    // Redis Pub/Sub 등으로 교체가 필요하다 - NotificationSseService 클래스 주석 참고
    @Operation(summary = "알림 실시간 스트림 구독 (SSE)")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        return notificationSseService.subscribe(authenticatedMember.memberId());
    }

    @Operation(summary = "내 알림 목록 조회 - 최신순")
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getNotifications(authenticatedMember.memberId(), pageable));
    }

    @Operation(summary = "읽지 않은 알림 개수 조회")
    @GetMapping("/unread-count")
    public ResponseEntity<NotificationUnreadCountResponse> unreadCount(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        long count = notificationService.getUnreadCount(authenticatedMember.memberId());
        return ResponseEntity.ok(new NotificationUnreadCountResponse(count));
    }

    @Operation(summary = "알림 단건 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(authenticatedMember.memberId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 알림 전체 읽음 처리")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        notificationService.markAllAsRead(authenticatedMember.memberId());
        return ResponseEntity.noContent().build();
    }
}
