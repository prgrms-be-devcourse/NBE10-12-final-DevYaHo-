package com.wellbuying.seller.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.dto.SignupResponse;
import com.wellbuying.member.repository.MemberRepository;
import com.wellbuying.member.service.EmailVerificationService;
import com.wellbuying.seller.domain.SellerInfo;
import com.wellbuying.seller.dto.SellerApplyRequest;
import com.wellbuying.seller.dto.SellerSignupRequest;
import com.wellbuying.seller.repository.SellerInfoRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerInfoService {

    private final SellerInfoRepository sellerInfoRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public SellerInfoService(SellerInfoRepository sellerInfoRepository, MemberRepository memberRepository,
            PasswordEncoder passwordEncoder, EmailVerificationService emailVerificationService) {
        this.sellerInfoRepository = sellerInfoRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    // 기존 회원의 셀러 신청 - 이미 신청/가입 이력이 있으면(status 무관) SELLER_APPLICATION_ALREADY_EXISTS 예외, 없으면 PENDING 상태로 생성
    @Transactional
    public void apply(Long memberId, SellerApplyRequest request) {
        if (sellerInfoRepository.existsByMemberId(memberId)) {
            throw new BusinessException(ErrorCode.SELLER_APPLICATION_ALREADY_EXISTS);
        }
        sellerInfoRepository.save(SellerInfo.apply(memberId, request.bankCode(), request.bankName(),
                request.accountNumber(), request.accountHolder(), request.companyName()));
    }

    // 판매자 다이렉트 가입 - 이메일 인증 완료 확인 후 MEMBERS(role=BUYER) + SELLER_INFO(status=PENDING)를 한 트랜잭션으로 생성
    @Transactional
    public SignupResponse signUp(SellerSignupRequest request) {
        emailVerificationService.assertVerified(request.email());
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = memberRepository.save(Member.signUp(request.email(), encodedPassword, request.name()));
        sellerInfoRepository.save(SellerInfo.apply(member.getId(), request.bankCode(), request.bankName(),
                request.accountNumber(), request.accountHolder(), request.companyName()));
        return SignupResponse.from(member);
    }
}
