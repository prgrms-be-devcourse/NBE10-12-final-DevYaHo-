package com.wellbuying.domain.member.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.auth.service.AuthService;
import com.wellbuying.auth.service.OAuthAccountService;
import com.wellbuying.domain.member.dto.EmailVerificationRequest;
import com.wellbuying.domain.member.dto.MemberResponse;
import com.wellbuying.domain.member.dto.SignupRequest;
import com.wellbuying.domain.member.dto.SignupResponse;
import com.wellbuying.domain.member.dto.SocialAccountsResponse;
import com.wellbuying.domain.member.dto.SocialLinkResponse;
import com.wellbuying.domain.member.dto.UpdateMemberRequest;
import com.wellbuying.domain.member.dto.VerifyEmailRequest;
import com.wellbuying.domain.member.service.EmailVerificationService;
import com.wellbuying.domain.member.service.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class MemberController {

    private final MemberService memberService;
    private final EmailVerificationService emailVerificationService;
    private final AuthService authService;
    private final OAuthAccountService oAuthAccountService;

    public MemberController(MemberService memberService, EmailVerificationService emailVerificationService,
            AuthService authService, OAuthAccountService oAuthAccountService) {
        this.memberService = memberService;
        this.emailVerificationService = emailVerificationService;
        this.authService = authService;
        this.oAuthAccountService = oAuthAccountService;
    }

    // 이메일 인증 코드 발송 API - 가입되지 않은 이메일이면 6자리 코드를 생성해 메일 발송하고 200 응답
    @PostMapping("/api/auth/email/verification-code")
    public ResponseEntity<Void> sendEmailVerificationCode(@Valid @RequestBody EmailVerificationRequest request) {
        emailVerificationService.sendVerificationCode(request.email());
        return ResponseEntity.ok().build();
    }

    // 이메일 인증 코드 검증 API - 코드가 일치하면 가입 허용 플래그를 저장하고 200 응답
    @PostMapping("/api/auth/email/verify")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyCode(request.email(), request.code());
        return ResponseEntity.ok().build();
    }

    // 회원가입 API - 이메일 인증 완료 여부를 확인한 뒤 이메일/비밀번호/이름을 받아 BUYER 회원을 생성하고 201 응답
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

    // 내 정보 수정 API - 이름/프로필 이미지를 수정하고 수정된 정보를 반환
    @PatchMapping("/api/members/me")
    public ResponseEntity<MemberResponse> updateMe(@AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody UpdateMemberRequest request) {
        MemberResponse response = memberService.updateProfile(authenticatedMember.memberId(), request);
        return ResponseEntity.ok(response);
    }

    // 회원 탈퇴 API - soft delete 처리 후 모든 기기의 세션을 무효화(logoutAll 재사용)
    @DeleteMapping("/api/members/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        memberService.withdraw(authenticatedMember.memberId());
        authService.logoutAll(authenticatedMember.memberId());
        return ResponseEntity.noContent().build();
    }

    // 연동된 소셜 계정 목록 조회 API
    @GetMapping("/api/members/me/social-accounts")
    public ResponseEntity<SocialAccountsResponse> getSocialAccounts(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        SocialAccountsResponse response = new SocialAccountsResponse(
                oAuthAccountService.getLinkedProviders(authenticatedMember.memberId()));
        return ResponseEntity.ok(response);
    }

    // 소셜 계정 추가 연동 API - 로그인 상태에서 OAuth2 인가 엔드포인트로 리다이렉트할 URL을 발급
    @PostMapping("/api/members/me/social-accounts/{provider}")
    public ResponseEntity<SocialLinkResponse> linkSocialAccount(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember, @PathVariable String provider,
            HttpServletRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromContextPath(request).toUriString();
        String redirectUrl = oAuthAccountService.issueLinkRedirectUrl(authenticatedMember.memberId(), provider,
                baseUrl);
        return ResponseEntity.ok(new SocialLinkResponse(redirectUrl));
    }

    // 소셜 연동 해제 API
    @DeleteMapping("/api/members/me/social-accounts/{provider}")
    public ResponseEntity<Void> unlinkSocialAccount(@AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable String provider) {
        oAuthAccountService.unlinkSocialAccount(authenticatedMember.memberId(), provider);
        return ResponseEntity.noContent().build();
    }
}
