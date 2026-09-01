package com.wellbuying.domain.seller.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.member.service.EmailVerificationService;
import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.entity.SellerStatus;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;
import com.wellbuying.domain.seller.dto.SellerApplyRequest;
import com.wellbuying.domain.seller.dto.SellerInfoResponse;
import com.wellbuying.domain.seller.dto.SellerSignupRequest;
import com.wellbuying.domain.seller.dto.SellerSignupResponse;
import com.wellbuying.domain.seller.repository.SellerInfoRepository;
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
        when(sellerInfoRepository.findByMemberId(1L)).thenReturn(Optional.empty());

        sellerInfoService.apply(1L,
                new SellerApplyRequest("088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));

        verify(sellerInfoRepository).save(any());
    }

    // PENDING/APPROVED 신청 이력이 있는 회원이 다시 신청하면 SELLER_APPLICATION_ALREADY_EXISTS 예외가 발생하고 저장이 일어나지 않는지 검증
    @Test
    void 이미_신청_이력이_있으면_셀러_신청에_실패한다() {
        when(sellerInfoRepository.findByMemberId(1L)).thenReturn(Optional.of(pendingSellerInfo(1L, 1L)));

        assertThatThrownBy(() -> sellerInfoService.apply(1L,
                new SellerApplyRequest("088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_APPLICATION_ALREADY_EXISTS);
        verify(sellerInfoRepository, never()).save(any());
    }

    // 거절(REJECTED)된 이력이 있는 회원이 재신청하면 기존 행이 PENDING으로 갱신되고 별도 저장은 호출되지 않는지 검증
    @Test
    void 거절된_회원이_재신청에_성공한다() {
        SellerInfo rejected = pendingSellerInfo(1L, 1L);
        rejected.reject();
        when(sellerInfoRepository.findByMemberId(1L)).thenReturn(Optional.of(rejected));

        sellerInfoService.apply(1L,
                new SellerApplyRequest("004", "국민은행", "110-987-654321", "김철수", "웰바잉스토어2"));

        assertThat(rejected.getStatus()).isEqualTo(SellerStatus.PENDING);
        assertThat(rejected.getBankName()).isEqualTo("국민은행");
        verify(sellerInfoRepository, never()).save(any());
    }

    // 다이렉트 셀러 가입 시 회원과 셀러 신청이 함께 저장되고 응답 role이 BUYER로 유지되는지 검증
    @Test
    void 판매자_다이렉트_가입에_성공한다() {
        when(memberRepository.existsByEmail("seller@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Pass1234!")).thenReturn("encoded-password");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(member, "id", 1L);
            return member;
        });

        SellerSignupResponse response = sellerInfoService.signUp(new SellerSignupRequest(
                "seller@example.com", "Pass1234!", "홍길동", "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));

        assertThat(response.memberId()).isEqualTo(1L);
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

    private SellerInfo pendingSellerInfo(Long id, Long memberId) {
        SellerInfo sellerInfo = SellerInfo.apply(memberId, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        ReflectionTestUtils.setField(sellerInfo, "id", id);
        return sellerInfo;
    }

    // PENDING 상태의 셀러 신청을 승인하면 status가 APPROVED로, 회원 role이 SELLER로 바뀌는지 검증
    @Test
    void 셀러_승인에_성공한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        Member member = Member.signUp("seller@example.com", "encoded-password", "홍길동");
        when(sellerInfoRepository.findById(1L)).thenReturn(Optional.of(sellerInfo));
        when(memberRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(member));

        sellerInfoService.approve(1L, 99L);

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.APPROVED);
        assertThat(member.getRole()).isEqualTo(Role.SELLER);
    }

    // PENDING 상태의 셀러 신청을 거절하면 status가 REJECTED로 바뀌고 role은 변경되지 않는지 검증
    @Test
    void 셀러_거절에_성공한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        when(sellerInfoRepository.findById(1L)).thenReturn(Optional.of(sellerInfo));

        sellerInfoService.reject(1L, 99L);

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.REJECTED);
        verify(memberRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    // 존재하지 않는 sellerId로 승인/거절 시도 시 SELLER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_셀러_신청은_승인_거절에_실패한다() {
        when(sellerInfoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerInfoService.approve(999L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_FOUND);
    }

    // 이미 처리된(PENDING이 아닌) 셀러 신청은 다시 승인/거절할 수 없는지 검증
    @Test
    void 이미_처리된_셀러_신청은_승인_거절에_실패한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        sellerInfo.approve();
        when(sellerInfoRepository.findById(1L)).thenReturn(Optional.of(sellerInfo));

        assertThatThrownBy(() -> sellerInfoService.reject(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_ALREADY_PROCESSED);
    }

    // APPROVED 상태의 셀러를 정지하면 status가 SUSPENDED로 바뀌는지 검증
    @Test
    void 셀러_정지에_성공한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        sellerInfo.approve();
        when(sellerInfoRepository.findById(1L)).thenReturn(Optional.of(sellerInfo));

        sellerInfoService.suspend(1L, 99L);

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    // APPROVED 상태가 아닌 셀러를 정지하려 하면 SELLER_NOT_APPROVED 예외가 발생하는지 검증
    @Test
    void APPROVED_상태가_아니면_셀러_정지에_실패한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        when(sellerInfoRepository.findById(1L)).thenReturn(Optional.of(sellerInfo));

        assertThatThrownBy(() -> sellerInfoService.suspend(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_APPROVED);
    }

    // 존재하지 않는 sellerId로 정지 시도 시 SELLER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_셀러는_정지에_실패한다() {
        when(sellerInfoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerInfoService.suspend(999L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_FOUND);
    }

    // SUSPENDED 상태의 셀러를 정지 복귀시키면 status가 다시 APPROVED로 바뀌는지 검증
    @Test
    void 셀러_정지_복귀에_성공한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        sellerInfo.approve();
        sellerInfo.suspend();
        when(sellerInfoRepository.findById(1L)).thenReturn(Optional.of(sellerInfo));

        sellerInfoService.reactivate(1L, 99L);

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.APPROVED);
    }

    // SUSPENDED 상태가 아닌 셀러를 정지 복귀시키려 하면 SELLER_NOT_SUSPENDED 예외가 발생하는지 검증
    @Test
    void SUSPENDED_상태가_아니면_셀러_정지_복귀에_실패한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        sellerInfo.approve();
        when(sellerInfoRepository.findById(1L)).thenReturn(Optional.of(sellerInfo));

        assertThatThrownBy(() -> sellerInfoService.reactivate(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_SUSPENDED);
    }

    // 존재하지 않는 sellerId로 정지 복귀 시도 시 SELLER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_셀러는_정지_복귀에_실패한다() {
        when(sellerInfoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerInfoService.reactivate(999L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_FOUND);
    }

    // 신청 이력이 있는 회원의 내 셀러 신청 상태 조회가 성공하는지 검증
    @Test
    void 내_셀러_신청_상태_조회에_성공한다() {
        SellerInfo sellerInfo = pendingSellerInfo(1L, 10L);
        when(sellerInfoRepository.findByMemberId(10L)).thenReturn(Optional.of(sellerInfo));

        SellerInfoResponse response = sellerInfoService.getMyStatus(10L);

        assertThat(response.status()).isEqualTo(SellerStatus.PENDING);
    }

    // 신청 이력이 없는 회원이 상태 조회 시 SELLER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 신청_이력이_없으면_내_셀러_신청_상태_조회에_실패한다() {
        when(sellerInfoRepository.findByMemberId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sellerInfoService.getMyStatus(10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_FOUND);
    }
}
