package com.wellbuying.domain.member.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.dto.MemberResponse;
import com.wellbuying.domain.member.dto.MemberSummaryResponse;
import com.wellbuying.domain.member.dto.SignupRequest;
import com.wellbuying.domain.member.dto.SignupResponse;
import com.wellbuying.domain.member.dto.UpdateMemberRequest;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.member.repository.SocialAccountRepository;
import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.entity.SellerStatus;
import com.wellbuying.domain.seller.repository.SellerInfoRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final SocialAccountRepository socialAccountRepository;
    private final SellerInfoRepository sellerInfoRepository;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService, SocialAccountRepository socialAccountRepository,
            SellerInfoRepository sellerInfoRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.socialAccountRepository = socialAccountRepository;
        this.sellerInfoRepository = sellerInfoRepository;
    }

    // 이메일 인증 완료 여부 확인 후, 이메일 중복 체크 → 비밀번호를 BCrypt로 인코딩하여 회원을 저장, 중복이면 EMAIL_ALREADY_EXISTS 예외
    @Transactional
    public SignupResponse signUp(SignupRequest request) {
        emailVerificationService.assertVerified(request.email());
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String encodedPassword = passwordEncoder.encode(request.password());
        Member member = Member.signUp(request.email(), encodedPassword, request.name(), request.phoneNumber());
        Member saved = memberRepository.save(member);
        return SignupResponse.from(saved);
    }

    // 탈퇴하지 않은 회원을 memberId로 조회, 없으면 MEMBER_NOT_FOUND 예외
    @Transactional(readOnly = true)
    public MemberResponse getMe(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        return MemberResponse.from(member);
    }

    // 탈퇴하지 않은 회원의 이름/프로필 이미지/전화번호를 수정, 없으면 MEMBER_NOT_FOUND 예외
    @Transactional
    public MemberResponse updateProfile(Long memberId, UpdateMemberRequest request) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.updateProfile(request.name(), request.profileImageUrl(), request.phoneNumber());
        return MemberResponse.from(member);
    }

    // 탈퇴하지 않은 회원을 soft delete하며 개인정보를 익명화, 연동된 소셜 계정을 전부 해제하고
    // PENDING/TERMINATED 셀러 신청 이력을 즉시 삭제 (ACTIVE 셀러의 금융 정보는 Phase 12까지 보존)
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.withdraw();
        socialAccountRepository.deleteAllByMemberId(memberId);
        sellerInfoRepository.findByMemberId(memberId)
                .filter(sellerInfo -> sellerInfo.getStatus() == SellerStatus.PENDING
                        || sellerInfo.getStatus() == SellerStatus.TERMINATED)
                .ifPresent(sellerInfoRepository::delete);
    }

    // 로그인 시점마다 호출 - lastLoginAt 갱신 (휴면 전환 차단은 AuthService.login()/OAuthAccountService에서 토큰 발급 전에 처리)
    // MemberLoginEventListener가 AuthService의 트랜잭션 커밋 이후 비동기로 호출하므로 부모 트랜잭션과 커넥션을 공유하지 않는다
    @Transactional
    public void updateLoginActivity(Long memberId) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!member.needsLastLoginUpdate()) {
            return;
        }
        member.recordLogin();
    }

    // 관리자 회원 목록 조회 - role/status로 선택적으로 필터링
    @Transactional(readOnly = true)
    public Page<MemberSummaryResponse> findMembers(Role role, MemberStatus status, Pageable pageable) {
        return memberRepository.search(role, status, pageable);
    }

    // 휴면 전환 배치 1건 처리 - MemberDormancyScheduler와 별도 빈이어야 @Transactional 프록시가 동작한다(자기 자신 호출 시 AOP 미적용)
    // 대상을 batchSize만큼만 조회해 벌크 UPDATE하고, 이 배치에서 실제로 전환된 건수를 반환 - 0이면 스케줄러가 반복을 종료
    @Transactional
    public int markDormantBatch(LocalDateTime threshold, int batchSize) {
        List<Long> ids = memberRepository.findIdsForDormancy(MemberStatus.ACTIVE, threshold, Limit.of(batchSize));
        if (ids.isEmpty()) {
            return 0;
        }
        return memberRepository.bulkMarkDormantByIds(MemberStatus.DORMANT, ids);
    }
}
