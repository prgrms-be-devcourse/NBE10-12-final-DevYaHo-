package com.wellbuying.auth.controller;

import com.wellbuying.auth.dto.LoginRequest;
import com.wellbuying.auth.dto.LoginResponse;
import com.wellbuying.auth.dto.OAuthExchangeRequest;
import com.wellbuying.auth.dto.ReissueRequest;
import com.wellbuying.auth.dto.ReissueResponse;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // 로그인 API - 이메일/비밀번호 검증 후 access/refresh 토큰 발급 (X-Device-Id 없으면 서버가 새로 발급)
    @PostMapping("/api/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        LoginResponse response = authService.login(request, deviceId);
        return ResponseEntity.ok(response);
    }

    // 토큰 재발급 API - body의 refresh token을 검증/rotate해 access/refresh 토큰을 새로 발급 (Bearer 인증 아님, permitAll)
    @PostMapping("/api/auth/reissue")
    public ResponseEntity<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        ReissueResponse response = authService.reissue(request);
        return ResponseEntity.ok(response);
    }

    // 로그아웃 API - access token의 deviceId claim으로 현재 기기의 세션만 삭제
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        authService.logout(authenticatedMember.memberId(), authenticatedMember.deviceId());
        return ResponseEntity.noContent().build();
    }

    // 전체 로그아웃 API - 계정의 모든 기기 세션을 삭제
    @PostMapping("/api/auth/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        authService.logoutAll(authenticatedMember.memberId());
        return ResponseEntity.noContent().build();
    }

    // 소셜 로그인 콜백에서 발급받은 1회용 교환 코드를 access/refresh 토큰으로 교환
    @PostMapping("/api/auth/oauth/exchange")
    public ResponseEntity<LoginResponse> exchangeOAuthCode(@Valid @RequestBody OAuthExchangeRequest request) {
        LoginResponse response = authService.exchangeOAuthCode(request.code());
        return ResponseEntity.ok(response);
    }
}
