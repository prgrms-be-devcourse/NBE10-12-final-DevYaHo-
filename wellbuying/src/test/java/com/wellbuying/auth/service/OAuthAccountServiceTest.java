package com.wellbuying.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.SocialAccount;
import com.wellbuying.member.repository.MemberRepository;
import com.wellbuying.member.repository.SocialAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @InjectMocks
    private OAuthAccountService oAuthAccountService;

    // (provider, providerId)로 연동된 소셜 계정이 있으면 그 계정의 회원을 그대로 반환하는지 검증
    @Test
    void 기존_소셜계정과_매칭되면_해당_회원을_반환한다() {
        SocialAccount socialAccount = SocialAccount.create(1L, "google", "google-uid-1");
        when(socialAccountRepository.findByProviderAndProviderId("google", "google-uid-1"))
                .thenReturn(Optional.of(socialAccount));
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        Member result = oAuthAccountService.findOrCreateMember("google", "google-uid-1", "test@example.com", "홍길동",
                null);

        assertThat(result).isEqualTo(member);
        verify(memberRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    // 소셜계정 매칭은 없지만 동일 이메일의 기존 회원이 있으면 새 소셜계정을 연동하고 그 회원을 반환하는지 검증
    @Test
    void 소셜계정_매칭은_없지만_동일_이메일_회원이_있으면_자동_연동한다() {
        when(socialAccountRepository.findByProviderAndProviderId("google", "google-uid-2"))
                .thenReturn(Optional.empty());
        Member member = Member.signUp("existing@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("existing@example.com")).thenReturn(Optional.of(member));

        Member result = oAuthAccountService.findOrCreateMember("google", "google-uid-2", "existing@example.com",
                "홍길동", null);

        assertThat(result).isEqualTo(member);
        verify(socialAccountRepository).save(any(SocialAccount.class));
        verify(memberRepository, never()).save(any());
    }

    // 매칭되는 소셜계정도, 동일 이메일 회원도 없으면 비밀번호 없는 신규 회원과 소셜계정을 생성하는지 검증
    @Test
    void 매칭되는_소셜계정도_이메일도_없으면_신규_회원을_생성한다() {
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "kakao-uid-1"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmailAndDeletedAtIsNull("new@example.com")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Member result = oAuthAccountService.findOrCreateMember("kakao", "kakao-uid-1", "new@example.com", "김철수",
                "https://example.com/profile.png");

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getName()).isEqualTo("김철수");
        assertThat(result.isSocialOnly()).isTrue();
        assertThat(result.getProfileImage()).isEqualTo("https://example.com/profile.png");
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    // 탈퇴한 회원의 이메일(유니크 제약으로 재사용 불가)이면 신규 생성 시 EMAIL_ALREADY_EXISTS 예외가 발생하는지 검증
    @Test
    void 탈퇴한_회원의_이메일이면_신규_생성시_예외가_발생한다() {
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "kakao-uid-2"))
                .thenReturn(Optional.empty());
        when(memberRepository.findByEmailAndDeletedAtIsNull("withdrawn@example.com")).thenReturn(Optional.empty());
        when(memberRepository.existsByEmail("withdrawn@example.com")).thenReturn(true);

        assertThatThrownBy(() -> oAuthAccountService.findOrCreateMember("kakao", "kakao-uid-2",
                "withdrawn@example.com", "이영희", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(memberRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }
}
