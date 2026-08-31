package com.wellbuying.auth.controller;

import com.wellbuying.auth.dto.DeviceSessionResponse;
import com.wellbuying.auth.dto.LoginRequest;
import com.wellbuying.auth.dto.LoginResponse;
import com.wellbuying.auth.dto.OAuthExchangeRequest;
import com.wellbuying.auth.dto.ReactivationRequest;
import com.wellbuying.auth.dto.ReissueRequest;
import com.wellbuying.auth.dto.ReissueResponse;
import com.wellbuying.auth.dto.VerifyReactivationRequest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.auth.service.AuthService;
import com.wellbuying.domain.member.service.EmailVerificationService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증", description = "로그인/토큰 재발급/로그아웃/소셜 로그인 교환/기기 목록")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
    }

    // 로그인 API - 이메일/비밀번호 검증 후 access/refresh 토큰 발급 (X-Device-Id 없으면 서버가 새로 발급, 휴면 계정이면 403)
    @Operation(summary = "로그인 - 이메일/비밀번호 검증 후 access/refresh 토큰 발급")
    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse response = authService.login(request, deviceId);
        return ResponseEntity.ok(response);
    }

    // 휴면 계정 재활성화 코드 발송 API - 휴면 상태인 회원만 요청 가능
    @Operation(summary = "휴면 계정 재활성화 코드 발송")
    @PostMapping("/api/auth/reactivation/send")
    public ResponseEntity<Void> sendReactivationCode(@Valid @RequestBody ReactivationRequest request) {
        emailVerificationService.sendReactivationCode(request.email());
        return ResponseEntity.ok().build();
    }

    // 휴면 계정 재활성화 코드 검증 API - 성공 시 즉시 ACTIVE로 전환하고 로그인 토큰까지 발급 (X-Device-Id 없으면 서버가 새로 발급)
    @Operation(summary = "휴면 계정 재활성화 코드 검증 - 성공 시 ACTIVE 전환 및 로그인 토큰 발급")
    @PostMapping("/api/auth/reactivation/verify")
    public ResponseEntity<LoginResponse> verifyReactivation(@Valid @RequestBody VerifyReactivationRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse response = authService.reactivate(request.email(), request.code(), deviceId);
        return ResponseEntity.ok(response);
    }

    // 토큰 재발급 API - body의 refresh token을 검증/rotate해 access/refresh 토큰을 새로 발급 (Bearer 인증 아님, permitAll)
    @Operation(summary = "토큰 재발급 - refresh token 검증/rotate 후 access/refresh 토큰 재발급")
    @PostMapping("/api/auth/reissue")
    public ResponseEntity<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        ReissueResponse response = authService.reissue(request);
        return ResponseEntity.ok(response);
    }

    // 로그아웃 API - access token의 deviceId claim으로 현재 기기의 세션만 삭제
    @Operation(summary = "로그아웃 - 현재 기기의 세션만 삭제")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        authService.logout(authenticatedMember.memberId(), authenticatedMember.deviceId());
        return ResponseEntity.noContent().build();
    }

    // 전체 로그아웃 API - 계정의 모든 기기 세션을 삭제
    @Operation(summary = "전체 로그아웃 - 계정의 모든 기기 세션 삭제")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/auth/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        authService.logoutAll(authenticatedMember.memberId());
        return ResponseEntity.noContent().build();
    }

    // 소셜 로그인 콜백에서 발급받은 1회용 교환 코드를 access/refresh 토큰으로 교환 (X-Device-Id 없으면 서버가 새로 발급)
    @Operation(summary = "소셜 로그인 교환 코드를 access/refresh 토큰으로 교환")
    @PostMapping("/api/auth/oauth/exchange")
    public ResponseEntity<LoginResponse> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse response = authService.exchangeOAuthCode(request.code(), deviceId);
        return ResponseEntity.ok(response);
    }

    // 로그인 기기 목록 조회 API - 현재 회원의 모든 활성 세션을 lastUsedAt 내림차순으로 반환
    @Operation(summary = "로그인 기기 목록 조회 - lastUsedAt 내림차순")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/api/auth/devices")
    public ResponseEntity<List<DeviceSessionResponse>> getDevices(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        List<DeviceSessionResponse> response = authService.getDevices(authenticatedMember.memberId());
        return ResponseEntity.ok(response);
    }
}
