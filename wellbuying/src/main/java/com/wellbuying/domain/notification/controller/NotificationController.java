package com.wellbuying.domain.notification.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.notification.dto.NotificationResponse;
import com.wellbuying.domain.notification.dto.NotificationUnreadCountResponse;
import com.wellbuying.domain.notification.service.NotificationService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "알림", description = "공동구매 성사/실패 알림 조회 및 읽음 처리")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "내 알림 목록 조회 - 최신순")
    @GetMapping("/api/notifications")
    public ResponseEntity<Page<NotificationResponse>> list(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getNotifications(authenticatedMember.memberId(), pageable));
    }

    @Operation(summary = "읽지 않은 알림 개수 조회")
    @GetMapping("/api/notifications/unread-count")
    public ResponseEntity<NotificationUnreadCountResponse> unreadCount(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        long count = notificationService.getUnreadCount(authenticatedMember.memberId());
        return ResponseEntity.ok(new NotificationUnreadCountResponse(count));
    }

    @Operation(summary = "알림 단건 읽음 처리")
    @PatchMapping("/api/notifications/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long notificationId) {
        notificationService.markAsRead(authenticatedMember.memberId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 알림 전체 읽음 처리")
    @PatchMapping("/api/notifications/read-all")
    public ResponseEntity<Void> markAllAsRead(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        notificationService.markAllAsRead(authenticatedMember.memberId());
        return ResponseEntity.noContent().build();
    }
}
