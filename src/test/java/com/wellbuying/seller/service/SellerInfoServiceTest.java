package com.wellbuying.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.dto.SignupResponse;
import com.wellbuying.member.repository.MemberRepository;
import com.wellbuying.member.service.EmailVerificationService;
import com.wellbuying.seller.dto.SellerApplyRequest;
import com.wellbuying.seller.dto.SellerSignupRequest;
import com.wellbuying.seller.repository.SellerInfoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SellerInfoServiceTest {

    @Mock
    private SellerInfoRepository sellerInfoRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private SellerInfoService sellerInfoService;

    // 신청 이력이 없는 회원이 셀러 신청 시 SellerInfo가 저장되는지 검증
    @Test
    void 기존_회원이_셀러_신청에_성공한다() {
        when(sellerInfoRepository.existsByMemberId(1L)).thenReturn(false);

        sellerInfoService.apply(1L,
                new SellerApplyRequest("088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));

        verify(sellerInfoRepository).save(any());
    }

    // 이미 신청 이력이 있는 회원이 다시 신청하면 SELLER_APPLICATION_ALREADY_EXISTS 예외가 발생하고 저장이 일어나지 않는지 검증
    @Test
    void 이미_신청_이력이_있으면_셀러_신청에_실패한다() {
        when(sellerInfoRepository.existsByMemberId(1L)).thenReturn(true);

        assertThatThrownBy(() -> sellerInfoService.apply(1L,
                new SellerApplyRequest("088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_APPLICATION_ALREADY_EXISTS);
        verify(sellerInfoRepository, never()).save(any());
    }

    // 다이렉트 셀러 가입 시 회원과 셀러 신청이 함께 저장되고 응답 role이 BUYER로 유지되는지 검증
    @Test
    void 판매자_다이렉트_가입에_성공한다() {
        when(memberRepository.existsByEmail("seller@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass1234!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = sellerInfoService.signUp(new SellerSignupRequest(
                "seller@example.com", "Pass1234!", "홍길동", "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));

        assertThat(response.email()).isEqualTo("seller@example.com");
        assertThat(response.role().name()).isEqualTo("BUYER");
        verify(sellerInfoRepository).save(any());
    }

    // 이메일 인증을 완료하지 않으면 다이렉트 가입 시 EMAIL_NOT_VERIFIED 예외가 발생하는지 검증
    @Test
    void 이메일_인증을_완료하지_않으면_다이렉트_가입에_실패한다() {
        doThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(emailVerificationService).assertVerified("not-verified@example.com");

        assertThatThrownBy(() -> sellerInfoService.signUp(new SellerSignupRequest(
                "not-verified@example.com", "Pass1234!", "홍길동", "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        verify(memberRepository, never()).save(any());
    }

    // 이미 가입된 이메일로 다이렉트 가입 시 EMAIL_ALREADY_EXISTS 예외가 발생하는지 검증
    @Test
    void 이메일이_이미_존재하면_다이렉트_가입에_실패한다() {
        when(memberRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> sellerInfoService.signUp(new SellerSignupRequest(
                "duplicate@example.com", "Pass1234!", "홍길동", "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(sellerInfoRepository, never()).save(any());
    }
}
