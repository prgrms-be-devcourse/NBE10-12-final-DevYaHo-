package com.wellbuying.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.auth.oauth.SocialLinkTicketRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.SocialAccount;
import com.wellbuying.member.repository.MemberRepository;
import com.wellbuying.member.repository.SocialAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private SocialLinkTicketRepository socialLinkTicketRepository;

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

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

    // 이미 다른(또는 같은) 회원에 연동된 (provider, providerId)면 추가 연동 시 예외가 발생하는지 검증
    @Test
    void 이미_연동된_소셜계정이면_추가연동시_예외가_발생한다() {
        SocialAccount existing = SocialAccount.create(2L, "google", "google-uid-1");
        when(socialAccountRepository.findByProviderAndProviderId("google", "google-uid-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> oAuthAccountService.linkSocialAccount(1L, "google", "google-uid-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        verify(socialAccountRepository, never()).save(any());
    }

    // 연동되지 않은 (provider, providerId)면 회원에 새 소셜계정을 연동하는지 검증
    @Test
    void 연동되지_않은_소셜계정이면_추가연동한다() {
        when(socialAccountRepository.findByProviderAndProviderId("google", "google-uid-3"))
                .thenReturn(Optional.empty());
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        Member result = oAuthAccountService.linkSocialAccount(1L, "google", "google-uid-3");

        assertThat(result).isEqualTo(member);
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    // 연동된 소셜계정 provider 목록을 대문자로 변환해 반환하는지 검증
    @Test
    void 연동된_소셜계정_목록을_대문자로_반환한다() {
        when(socialAccountRepository.findAllByMemberId(1L)).thenReturn(
                List.of(SocialAccount.create(1L, "google", "google-uid-1"),
                        SocialAccount.create(1L, "kakao", "kakao-uid-1")));

        List<String> result = oAuthAccountService.getLinkedProviders(1L);

        assertThat(result).containsExactly("GOOGLE", "KAKAO");
    }

    // 연동되지 않은 provider를 해제하려 하면 예외가 발생하는지 검증
    @Test
    void 연동되지_않은_provider_해제시_예외가_발생한다() {
        when(socialAccountRepository.findAllByMemberId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> oAuthAccountService.unlinkSocialAccount(1L, "google"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND);
        verify(socialAccountRepository, never()).delete(any());
    }

    // 비밀번호 없는 회원의 마지막 연동 소셜계정을 해제하려 하면 예외가 발생하는지 검증
    @Test
    void 마지막_로그인_수단이면_해제시_예외가_발생한다() {
        SocialAccount socialAccount = SocialAccount.create(1L, "google", "google-uid-1");
        when(socialAccountRepository.findAllByMemberId(1L)).thenReturn(List.of(socialAccount));
        Member member = Member.socialOnly("test@example.com", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> oAuthAccountService.unlinkSocialAccount(1L, "google"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_LAST_LOGIN_METHOD);
        verify(socialAccountRepository, never()).delete(any());
    }

    // 비밀번호가 있거나 연동된 소셜계정이 여러 개면 정상적으로 해제되는지 검증
    @Test
    void 마지막_로그인_수단이_아니면_정상_해제된다() {
        SocialAccount socialAccount = SocialAccount.create(1L, "google", "google-uid-1");
        when(socialAccountRepository.findAllByMemberId(1L)).thenReturn(List.of(socialAccount));
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));

        oAuthAccountService.unlinkSocialAccount(1L, "google");

        verify(socialAccountRepository).delete(socialAccount);
    }

    // 등록되지 않은 provider면 연동 리다이렉트 URL 발급시 예외가 발생하는지 검증
    @Test
    void 등록되지_않은_provider면_링크URL_발급시_예외가_발생한다() {
        when(clientRegistrationRepository.findByRegistrationId("naver")).thenReturn(null);

        assertThatThrownBy(() -> oAuthAccountService.issueLinkRedirectUrl(1L, "naver", "https://api.wellbuying.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    // 등록된 provider면 link_token을 발급해 인가 엔드포인트 URL을 조립하는지 검증
    @Test
    void 등록된_provider면_링크URL을_발급한다() {
        when(clientRegistrationRepository.findByRegistrationId("google")).thenReturn(mock(ClientRegistration.class));
        when(socialLinkTicketRepository.issue(1L)).thenReturn("link-token-1");

        String result = oAuthAccountService.issueLinkRedirectUrl(1L, "google", "https://api.wellbuying.com");

        assertThat(result).isEqualTo("https://api.wellbuying.com/oauth2/authorization/google?link_token=link-token-1");
    }
}
