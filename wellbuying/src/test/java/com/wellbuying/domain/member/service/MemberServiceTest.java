package com.wellbuying.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.dto.MemberResponse;
import com.wellbuying.domain.member.dto.MemberSummaryResponse;
import com.wellbuying.domain.member.dto.SignupRequest;
import com.wellbuying.domain.member.dto.SignupResponse;
import com.wellbuying.domain.member.dto.UpdateMemberRequest;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.repository.SocialAccountRepository;
import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.entity.SellerStatus;
import com.wellbuying.domain.seller.repository.SellerInfoRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SellerInfoRepository sellerInfoRepository;

    @InjectMocks
    private MemberService memberService;

    // 회원가입 시 원문 비밀번호가 인코딩된 상태로 저장되고 응답에 이메일/이름/role이 담기는지 검증
    @Test
    void 회원가입시_비밀번호를_인코딩하여_회원을_저장한다() {
        when(memberRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass1234!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = memberService.signUp(
                new SignupRequest("test@example.com", "Pass1234!", "홍길동", null));

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.role()).isEqualTo(Role.BUYER);
        verify(passwordEncoder).encode("Pass1234!");
    }

    // 이미 가입된 이메일로 회원가입 시 EMAIL_ALREADY_EXISTS 예외가 발생하고 저장이 일어나지 않는지 검증
    @Test
    void 이메일이_이미_존재하면_회원가입시_예외가_발생한다() {
        when(memberRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signUp(
                new SignupRequest("duplicate@example.com", "Pass1234!", "홍길동", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(memberRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // 이메일 인증을 완료하지 않은 상태로 회원가입 시 EMAIL_NOT_VERIFIED 예외가 발생하고 이메일 중복 체크가 일어나지 않는지 검증
    @Test
    void 이메일_인증을_완료하지_않으면_회원가입시_예외가_발생한다() {
        doThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(emailVerificationService).assertVerified("not-verified@example.com");

        assertThatThrownBy(() -> memberService.signUp(
                new SignupRequest("not-verified@example.com", "Pass1234!", "홍길동", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    // 존재하는 회원 ID로 조회 시 해당 회원 정보를 응답으로 반환하는지 검증
    @Test
    void 존재하는_회원ID로_조회하면_회원정보를_반환한다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        MemberResponse response = memberService.getMe(1L);

        assertThat(response.email()).isEqualTo("me@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
    }

    // 존재하지 않는 회원 ID로 조회 시 MEMBER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_회원ID로_조회하면_예외가_발생한다() {
        when(memberRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMe(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // 존재하는 회원의 이름/프로필 이미지를 수정하면 수정된 값이 응답에 반영되는지 검증
    @Test
    void 존재하는_회원의_정보를_수정한다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        MemberResponse response = memberService.updateProfile(1L,
                new UpdateMemberRequest("김철수", "https://example.com/profile.png", null));

        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.png");
    }

    // 존재하지 않거나 이미 탈퇴한 회원 ID로 정보 수정 시 MEMBER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_회원ID로_정보를_수정하면_예외가_발생한다() {
        when(memberRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateProfile(999L, new UpdateMemberRequest("김철수", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // 존재하는 회원을 탈퇴시키면 deletedAt이 세팅되는지 검증
    @Test
    void 존재하는_회원을_탈퇴시킨다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        memberService.withdraw(1L);

        assertThat(member.getDeletedAt()).isNotNull();
    }

    // 존재하지 않거나 이미 탈퇴한 회원 ID로 탈퇴 시도 시 MEMBER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_회원ID로_탈퇴하면_예외가_발생한다() {
        when(memberRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.withdraw(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // 탈퇴 시 연동된 소셜 계정이 모두 삭제되는지 검증
    @Test
    void 탈퇴시_연동된_소셜_계정이_모두_삭제된다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        memberService.withdraw(1L);

        verify(socialAccountRepository).deleteAllByMemberId(1L);
    }

    // 탈퇴 시 PENDING/TERMINATED 셀러 신청 이력은 즉시 삭제되는지 검증
    @Test
    void 탈퇴시_PENDING_셀러_신청은_삭제된다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        SellerInfo pending = SellerInfo.apply(1L, "004", "국민은행", "123456", "홍길동", "회사");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(sellerInfoRepository.findByMemberId(1L)).thenReturn(Optional.of(pending));

        memberService.withdraw(1L);

        verify(sellerInfoRepository).delete(pending);
    }

    // 탈퇴 시 ACTIVE 셀러(금융 정보 보유)는 Phase 12까지 삭제하지 않는지 검증
    @Test
    void 탈퇴시_ACTIVE_셀러_정보는_삭제하지_않는다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        SellerInfo active = SellerInfo.apply(1L, "004", "국민은행", "123456", "홍길동", "회사");
        active.approve();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(sellerInfoRepository.findByMemberId(1L)).thenReturn(Optional.of(active));

        memberService.withdraw(1L);

        verify(sellerInfoRepository, never()).delete(any());
    }

    // lastLoginAt이 없어 갱신이 필요한 회원은 로그인 시 lastLoginAt이 기록되는지 검증
    @Test
    void 로그인시_lastLoginAt이_없으면_기록된다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        memberService.updateLoginActivity(1L);

        assertThat(member.getLastLoginAt()).isNotNull();
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // 스로틀 시간 내에 재로그인하면 lastLoginAt이 갱신되지 않는지 검증
    @Test
    void 스로틀_시간내_재로그인시_lastLoginAt이_갱신되지_않는다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        member.recordLogin();
        LocalDateTime firstLoginAt = member.getLastLoginAt();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        memberService.updateLoginActivity(1L);

        assertThat(member.getLastLoginAt()).isEqualTo(firstLoginAt);
    }

    // 휴면 전환 대상(마지막 로그인 6개월 경과) 회원이 재로그인하면 DORMANT를 거쳐 lastLoginAt이 갱신되는지 검증
    @Test
    void 휴면_전환_대상_회원이_재로그인하면_DORMANT로_전환된_후_lastLoginAt이_갱신된다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        ReflectionTestUtils.setField(member, "lastLoginAt", LocalDateTime.now().minusMonths(7));
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        memberService.updateLoginActivity(1L);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DORMANT);
        assertThat(member.getLastLoginAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    // 회원 목록 조회는 QueryDSL 리포지토리의 search 결과를 그대로 위임/반환하는지 검증
    @Test
    void 회원목록조회는_repository의_search_결과를_반환한다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        MemberSummaryResponse summary = new MemberSummaryResponse(1L, member.getEmail(), member.getName(),
                Role.BUYER, MemberStatus.ACTIVE, null, member.getCreatedAt());
        PageRequest pageable = PageRequest.of(0, 20);
        PageImpl<MemberSummaryResponse> page = new PageImpl<>(java.util.List.of(summary), pageable, 1);
        when(memberRepository.search(Role.BUYER, MemberStatus.ACTIVE, pageable)).thenReturn(page);

        var result = memberService.findMembers(Role.BUYER, MemberStatus.ACTIVE, pageable);

        assertThat(result).containsExactly(summary);
        verify(memberRepository).search(eq(Role.BUYER), eq(MemberStatus.ACTIVE), eq(pageable));
    }
}
