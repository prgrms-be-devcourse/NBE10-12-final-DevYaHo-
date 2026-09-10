package com.wellbuying.domain.payment.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.order.dto.OrderDetailResponse;
import com.wellbuying.domain.order.service.OrderQueryService;
import com.wellbuying.domain.payment.service.PaymentRetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 결제 실패한 주문을 구매자가 직접 재시도한다. 본인 것만 다루므로 경로에 memberId를 두지 않고 토큰에서 꺼낸다
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRetryService paymentRetryService;
    private final OrderQueryService orderQueryService;

    public PaymentController(PaymentRetryService paymentRetryService, OrderQueryService orderQueryService) {
        this.paymentRetryService = paymentRetryService;
        this.orderQueryService = orderQueryService;
    }

    // 재시도는 실패했던 주문은 그대로 두고 새 주문을 만들어 처리하므로, 새로 만들어진 주문의 상세를 돌려준다.
    // URI는 id 뒤에 동사를 붙이는 프로젝트 컨벤션을 따른다 (AdminGroupBuyController의 approve/reject 참고)
    @PostMapping("/{orderId}/retry")
    public ResponseEntity<OrderDetailResponse> retry(@AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable String orderId) {
        String newOrderId = paymentRetryService.retry(member.memberId(), orderId);
        return ResponseEntity.ok(orderQueryService.getMyOrderDetail(member.memberId(), newOrderId));
    }
}
