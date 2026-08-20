package com.wellbuying.member.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.member.dto.MemberResponse;
import com.wellbuying.member.dto.SignupRequest;
import com.wellbuying.member.dto.SignupResponse;
import com.wellbuying.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 회원가입 API - 이메일/비밀번호/이름을 받아 BUYER 회원을 생성하고 201 응답
    @PostMapping("/api/auth/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 내 정보 조회 API - JWT에서 추출한 memberId로 로그인한 회원 정보 반환
    @GetMapping("/api/members/me")
    public ResponseEntity<MemberResponse> me(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        MemberResponse response = memberService.getMe(authenticatedMember.memberId());
        return ResponseEntity.ok(response);
    }
}
