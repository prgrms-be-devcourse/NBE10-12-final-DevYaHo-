package com.wellbuying.domain.seller.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.member.service.EmailVerificationService;
import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.entity.SellerStatus;
import com.wellbuying.domain.seller.dto.SellerApplyRequest;
import com.wellbuying.domain.seller.dto.SellerInfoResponse;
import com.wellbuying.domain.seller.dto.SellerSignupRequest;
import com.wellbuying.domain.seller.dto.SellerSignupResponse;
import com.wellbuying.domain.seller.repository.SellerInfoRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 기존 회원의 셀러 신청 - 신청 이력이 없으면 PENDING으로 신규 생성, TERMINATED(거절) 이력이 있으면 재신청으로 갱신, 그 외(PENDING/ACTIVE) 이력이 있으면 예외
    @Transactional
    public void apply(Long memberId, SellerApplyRequest request) {
        Optional<SellerInfo> existing = sellerInfoRepository.findByMemberId(memberId);
        if (existing.isPresent()) {
            SellerInfo sellerInfo = existing.get();
            if (sellerInfo.getStatus() != SellerStatus.TERMINATED) {
                throw new BusinessException(ErrorCode.SELLER_APPLICATION_ALREADY_EXISTS);
            }
            sellerInfo.reapply(request.bankCode(), request.bankName(), request.accountNumber(),
                    request.accountHolder(), request.companyName());
            return;
        }
        sellerInfoRepository.save(SellerInfo.apply(memberId, request.bankCode(), request.bankName(),
                request.accountNumber(), request.accountHolder(), request.companyName()));
    }

    // 판매자 다이렉트 가입 - 이메일 인증 완료 확인 후 MEMBERS(role=BUYER) + SELLER_INFO(status=PENDING)를 한 트랜잭션으로 생성
    @Transactional
    public SellerSignupResponse signUp(SellerSignupRequest request) {
        emailVerificationService.assertVerified(request.email());
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = memberRepository.save(Member.signUp(request.email(), encodedPassword, request.name()));
        sellerInfoRepository.save(SellerInfo.apply(member.getId(), request.bankCode(), request.bankName(),
                request.accountNumber(), request.accountHolder(), request.companyName()));
        return SellerSignupResponse.from(member);
    }

    // 관리자의 상태별 셀러 신청 목록 조회
    @Transactional(readOnly = true)
    public Page<SellerInfoResponse> findByStatus(SellerStatus status, Pageable pageable) {
        return sellerInfoRepository.findAllByStatus(status, pageable).map(SellerInfoResponse::from);
    }

    // 내 셀러 신청 상태 조회 - 신청 이력이 없으면 SELLER_NOT_FOUND
    @Transactional(readOnly = true)
    public SellerInfoResponse getMyStatus(Long memberId) {
        SellerInfo sellerInfo = sellerInfoRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        return SellerInfoResponse.from(sellerInfo);
    }

    // 셀러 승인 - PENDING 상태가 아니면 SELLER_ALREADY_PROCESSED, 통과하면 SELLER_INFO를 ACTIVE로 전환하고 MEMBERS.role을 SELLER로 변경
    @Transactional
    public void approve(Long sellerId) {
        SellerInfo sellerInfo = findPendingSellerInfo(sellerId);
        sellerInfo.approve();
        Member member = memberRepository.findByIdAndDeletedAtIsNull(sellerInfo.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.activateAsSeller();
    }

    // 셀러 거절 - PENDING 상태가 아니면 SELLER_ALREADY_PROCESSED, 통과하면 SELLER_INFO를 TERMINATED로 전환 (role은 변경하지 않음)
    @Transactional
    public void reject(Long sellerId) {
        SellerInfo sellerInfo = findPendingSellerInfo(sellerId);
        sellerInfo.reject();
    }

    private SellerInfo findPendingSellerInfo(Long sellerId) {
        SellerInfo sellerInfo = sellerInfoRepository.findById(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SELLER_NOT_FOUND));
        if (sellerInfo.getStatus() != SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.SELLER_ALREADY_PROCESSED);
        }
        return sellerInfo;
    }
}
