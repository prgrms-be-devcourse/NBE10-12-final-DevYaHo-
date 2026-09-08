package com.wellbuying.domain.order.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.order.dto.OrderDetailResponse;
import com.wellbuying.domain.order.dto.OrderSummaryResponse;
import com.wellbuying.domain.order.service.OrderQueryService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "주문", description = "결제/주문 내역 조회")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class OrderController {

    private final OrderQueryService orderQueryService;

    public OrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @Operation(summary = "내 결제/주문 내역 목록 조회 - 최신순")
    @GetMapping("/me")
    public ResponseEntity<Page<OrderSummaryResponse>> myOrders(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(orderQueryService.getMyOrders(member.memberId(), pageable));
    }

    @Operation(summary = "내 주문 1건의 결제 정보 상세 조회")
    @GetMapping("/me/{orderId}")
    public ResponseEntity<OrderDetailResponse> myOrderDetail(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable String orderId) {
        return ResponseEntity.ok(orderQueryService.getMyOrderDetail(member.memberId(), orderId));
    }
}
