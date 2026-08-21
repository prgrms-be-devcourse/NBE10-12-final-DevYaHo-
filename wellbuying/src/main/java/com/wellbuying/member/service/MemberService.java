package com.wellbuying.member.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.dto.MemberResponse;
import com.wellbuying.member.dto.SignupRequest;
import com.wellbuying.member.dto.SignupResponse;
import com.wellbuying.member.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    // 이메일 인증 완료 여부 확인 후, 이메일 중복 체크 → 비밀번호를 BCrypt로 인코딩하여 회원을 저장, 중복이면 EMAIL_ALREADY_EXISTS 예외
    @Transactional
    public SignupResponse signUp(SignupRequest request) {
        emailVerificationService.assertVerified(request.email());
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = Member.signUp(request.email(), encodedPassword, request.name());
        Member saved = memberRepository.save(member);
        return SignupResponse.from(saved);
    }

    // memberId로 회원을 조회, 없으면 MEMBER_NOT_FOUND 예외
    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponse.from(member);
    }
}
