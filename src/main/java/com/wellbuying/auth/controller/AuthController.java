package com.wellbuying.auth.controller;

import com.wellbuying.auth.dto.LoginRequest;
import com.wellbuying.auth.dto.LoginResponse;
import com.wellbuying.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
}
