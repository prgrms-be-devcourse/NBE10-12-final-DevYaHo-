package com.wellbuying.domain.payment.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.payment.dto.BillingKeyAuthRequestResponse;
import com.wellbuying.domain.payment.dto.BillingKeyRegisterRequest;
import com.wellbuying.domain.payment.dto.BillingKeyResponse;
import com.wellbuying.domain.payment.service.BillingKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 카드 등록/조회/폐기. 전부 본인 것만 다루므로 경로에 memberId를 두지 않고 토큰에서 꺼낸다
@RestController
@RequestMapping("/api/payments/billing-key")
public class BillingKeyController {

    private final BillingKeyService billingKeyService;

    public BillingKeyController(BillingKeyService billingKeyService) {
        this.billingKeyService = billingKeyService;
    }

    // 카드 등록 창에 넘길 customerKey 발급 (기존에 발급받은 값이 있으면 그대로 재사용된다)
    @PostMapping("/auth-request")
    public ResponseEntity<BillingKeyAuthRequestResponse> authRequest(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ResponseEntity.ok(billingKeyService.issueCustomerKey(member.memberId()));
    }

    // 결제창이 돌려준 authKey를 빌링키로 교환해 저장 (이미 등록된 카드가 있으면 폐기 후 교체)
    @PostMapping
    public ResponseEntity<BillingKeyResponse> register(@AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody BillingKeyRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billingKeyService.register(member.memberId(), request.authKey(), request.customerKey()));
    }

    // 등록 여부 + 표시용 카드 정보
    @GetMapping
    public ResponseEntity<BillingKeyResponse> find(@AuthenticationPrincipal AuthenticatedMember member) {
        return ResponseEntity.ok(billingKeyService.find(member.memberId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> discard(@AuthenticationPrincipal AuthenticatedMember member) {
        billingKeyService.discard(member.memberId());
        return ResponseEntity.noContent().build();
    }
}
