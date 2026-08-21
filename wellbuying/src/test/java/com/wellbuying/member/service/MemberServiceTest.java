package com.wellbuying.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.Role;
import com.wellbuying.member.dto.MemberResponse;
import com.wellbuying.member.dto.SignupRequest;
import com.wellbuying.member.dto.SignupResponse;
import com.wellbuying.member.dto.UpdateMemberRequest;
import com.wellbuying.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private MemberService memberService;

    // 회원가입 시 원문 비밀번호가 인코딩된 상태로 저장되고 응답에 이메일/이름/role이 담기는지 검증
    @Test
    void 회원가입시_비밀번호를_인코딩하여_회원을_저장한다() {
        when(memberRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass1234!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = memberService.signUp(new SignupRequest("test@example.com", "Pass1234!", "홍길동"));

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.role()).isEqualTo(Role.BUYER);
        verify(passwordEncoder).encode("Pass1234!");
    }

    // 이미 가입된 이메일로 회원가입 시 EMAIL_ALREADY_EXISTS 예외가 발생하고 저장이 일어나지 않는지 검증
    @Test
    void 이메일이_이미_존재하면_회원가입시_예외가_발생한다() {
        when(memberRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> memberService.signUp(new SignupRequest("duplicate@example.com", "Pass1234!", "홍길동")))
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
                new SignupRequest("not-verified@example.com", "Pass1234!", "홍길동")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        verify(memberRepository, never()).existsByEmail(anyString());
    }

    // 존재하는 회원 ID로 조회 시 해당 회원 정보를 응답으로 반환하는지 검증
    @Test
    void 존재하는_회원ID로_조회하면_회원정보를_반환한다() {
        Member member = Member.signUp("me@example.com", "encoded-password", "홍길동");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberResponse response = memberService.getMe(1L);

        assertThat(response.email()).isEqualTo("me@example.com");
        assertThat(response.name()).isEqualTo("홍길동");
    }

    // 존재하지 않는 회원 ID로 조회 시 MEMBER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_회원ID로_조회하면_예외가_발생한다() {
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

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
                new UpdateMemberRequest("김철수", "https://example.com/profile.png"));

        assertThat(response.name()).isEqualTo("김철수");
        assertThat(response.profileImageUrl()).isEqualTo("https://example.com/profile.png");
    }

    // 존재하지 않거나 이미 탈퇴한 회원 ID로 정보 수정 시 MEMBER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_회원ID로_정보를_수정하면_예외가_발생한다() {
        when(memberRepository.findByIdAndDeletedAtIsNull(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.updateProfile(999L, new UpdateMemberRequest("김철수", null)))
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
}
