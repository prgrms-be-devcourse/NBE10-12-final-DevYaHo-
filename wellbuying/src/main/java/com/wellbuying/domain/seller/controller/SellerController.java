package com.wellbuying.domain.seller.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.seller.dto.SellerApplyRequest;
import com.wellbuying.domain.seller.dto.SellerInfoResponse;
import com.wellbuying.domain.seller.dto.SellerSignupRequest;
import com.wellbuying.domain.seller.dto.SellerSignupResponse;
import com.wellbuying.domain.seller.service.SellerInfoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SellerController {

    private final SellerInfoService sellerInfoService;

    public SellerController(SellerInfoService sellerInfoService) {
        this.sellerInfoService = sellerInfoService;
    }

    // 기존 회원의 셀러 신청 API - 은행/사업자 정보를 받아 PENDING 상태로 신청, 이미 신청 이력이 있으면 409
    @PostMapping("/api/auth/seller/apply")
    public ResponseEntity<Void> apply(@AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody SellerApplyRequest request) {
        sellerInfoService.apply(authenticatedMember.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 판매자 다이렉트 가입 API - 이메일 인증 완료 확인 후 계정 생성과 셀러 신청을 한 번에 처리 (승인 전까지 role은 BUYER 유지)
    @PostMapping("/api/auth/seller/signup")
    public ResponseEntity<SellerSignupResponse> signUp(@Valid @RequestBody SellerSignupRequest request) {
        SellerSignupResponse response = sellerInfoService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 내 셀러 신청 상태 조회 API - 신청 이력이 없으면 404
    @GetMapping("/api/members/me/seller-info")
    public ResponseEntity<SellerInfoResponse> getMySellerInfo(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        SellerInfoResponse response = sellerInfoService.getMyStatus(authenticatedMember.memberId());
        return ResponseEntity.ok(response);
    }
}
