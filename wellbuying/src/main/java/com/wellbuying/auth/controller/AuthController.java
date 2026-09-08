package com.wellbuying.auth.controller;

import com.wellbuying.auth.dto.*;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.auth.jwt.JwtProperties;
import com.wellbuying.auth.service.AuthService;
import com.wellbuying.domain.member.service.EmailVerificationService;
import com.wellbuying.global.config.OpenApiConfig;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "인증", description = "로그인/토큰 재발급/로그아웃/소셜 로그인 교환/기기 목록")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, EmailVerificationService emailVerificationService,
            JwtProperties jwtProperties) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.jwtProperties = jwtProperties;
    }

    // refresh token을 httpOnly 쿠키로 내려보낸다 - Path를 /api/auth로 좁혀 다른 API 요청에는 딸려가지 않게 한다
    private ResponseCookie buildRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(jwtProperties.refreshTokenExpirationMs() / 1000)
                .build();
    }

    // 로그아웃 시 브라우저에 남은 refresh token 쿠키를 즉시 만료시켜 지운다
    private ResponseCookie expireRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
    }

    // 로그인 API - 이메일/비밀번호 검증 후 access/refresh 토큰 발급 (X-Device-Id 없으면 서버가 새로 발급, 휴면 계정이면 403)
    @Operation(summary = "로그인 - 이메일/비밀번호 검증 후 access/refresh 토큰 발급")
    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse response = authService.login(request, deviceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(response.refreshToken()).toString())
                .body(response);
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
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(response.refreshToken()).toString())
                .body(response);
    }

    // 비밀번호 재발급 인증 코드 발송 API - 비로그인 상태에서도 이메일만으로 요청 가능 (소셜 전용 계정은 403)
    @Operation(summary = "비밀번호 재발급 인증 코드 발송")
    @PostMapping("/api/auth/password-reissue/send")
    public ResponseEntity<Void> sendPasswordReissueCode(@Valid @RequestBody PasswordReissueSendRequest request) {
        emailVerificationService.sendPasswordReissueCode(request.email());
        return ResponseEntity.ok().build();
    }

    // 비밀번호 재발급 인증 코드 검증 API - 성공 시 verified 플래그만 남기고, 실제 비밀번호 교체는 reset API에서 별도로 처리
    @Operation(summary = "비밀번호 재발급 인증 코드 검증")
    @PostMapping("/api/auth/password-reissue/verify")
    public ResponseEntity<Void> verifyPasswordReissueCode(@Valid @RequestBody PasswordReissueVerifyRequest request) {
        emailVerificationService.verifyPasswordReissueCode(request.email(), request.code());
        return ResponseEntity.ok().build();
    }

    // 비밀번호 재설정 API - verify 단계의 verified 플래그를 확인한 뒤 비밀번호를 교체하고 전체 기기 세션을 무효화
    @Operation(summary = "비밀번호 재설정 - verified 플래그 확인 후 비밀번호 교체 및 전체 로그아웃")
    @PostMapping("/api/auth/password-reissue/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordReissueResetRequest request) {
        authService.resetPassword(request.email(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // 토큰 재발급 API - httpOnly 쿠키의 refresh token을 검증/rotate해 access/refresh 토큰을 새로 발급 (Bearer 인증 아님, permitAll)
    // X-Device-Id 헤더를 필수로 요구해 CSRF 방어를 한 겹 더한다 - 커스텀 헤더는 form/img 태그로는 못 실어 보내고
    // fetch/XHR만 가능하므로 브라우저가 CORS preflight를 강제하게 되어, 허용된 Origin이 아니면 애초에 요청이 막힌다(phase25 §2-4)
    @Operation(summary = "토큰 재발급 - refresh token 검증/rotate 후 access/refresh 토큰 재발급")
    @PostMapping("/api/auth/reissue")
    public ResponseEntity<ReissueResponse> reissue(
            @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        if (refreshToken == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(ErrorCode.DEVICE_ID_REQUIRED);
        }
        ReissueResponse response = authService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(response.refreshToken()).toString())
                .body(response);
    }

    // 로그아웃 API - access token의 deviceId claim으로 현재 기기의 세션만 삭제
    @Operation(summary = "로그아웃 - 현재 기기의 세션만 삭제")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        authService.logout(authenticatedMember.memberId(), authenticatedMember.deviceId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expireRefreshTokenCookie().toString())
                .build();
    }

    // 전체 로그아웃 API - 계정의 모든 기기 세션을 삭제
    @Operation(summary = "전체 로그아웃 - 계정의 모든 기기 세션 삭제")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/api/auth/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        authService.logoutAll(authenticatedMember.memberId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expireRefreshTokenCookie().toString())
                .build();
    }

    // 소셜 로그인 콜백에서 발급받은 1회용 교환 코드를 access/refresh 토큰으로 교환 (X-Device-Id 없으면 서버가 새로 발급)
    @Operation(summary = "소셜 로그인 교환 코드를 access/refresh 토큰으로 교환")
    @PostMapping("/api/auth/oauth/exchange")
    public ResponseEntity<LoginResponse> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse response = authService.exchangeOAuthCode(request.code(), deviceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshTokenCookie(response.refreshToken()).toString())
                .body(response);
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
