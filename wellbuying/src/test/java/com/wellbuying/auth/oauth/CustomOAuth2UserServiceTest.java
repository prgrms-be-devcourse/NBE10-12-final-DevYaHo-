package com.wellbuying.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.auth.service.OAuthAccountService;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private OAuthAccountService oAuthAccountService;

    @Mock
    private SocialLinkTicketRepository socialLinkTicketRepository;

    private MockHttpServletRequest httpServletRequest;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        httpServletRequest = new MockHttpServletRequest();
        service = new CustomOAuth2UserService(oAuthAccountService, socialLinkTicketRepository, httpServletRequest);
    }

    private ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }

    private OAuth2UserRequest userRequest(Map<String, Object> attributes) {
        ClientRegistration registration = googleRegistration();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token-value",
                Instant.now(), Instant.now().plusSeconds(3600), Set.of("email"));
        RestOperations restOperations = mock(RestOperations.class);
        when(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(attributes));
        service.setRestOperations(restOperations);
        return new OAuth2UserRequest(registration, accessToken);
    }

    @Test
    @DisplayName("연동 중이 아니고 신규/기존 회원이면 findOrCreateMember로 로그인 처리하고 linked=false인 principal을 반환한다")
    void 신규_로그인() {
        httpServletRequest.setParameter("state", "state-1");
        when(socialLinkTicketRepository.consumeByState("state-1")).thenReturn(Optional.empty());
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(10L);
        when(member.getRole()).thenReturn(Role.BUYER);
        when(oAuthAccountService.findOrCreateMember("google", "google-sub-1", "user@example.com", "홍길동", "pic.jpg"))
                .thenReturn(member);

        OAuth2User result = service.loadUser(
                userRequest(Map.of("sub", "google-sub-1", "email", "user@example.com", "name", "홍길동", "picture",
                        "pic.jpg")));

        OAuthPrincipal principal = (OAuthPrincipal) result;
        assertThat(principal.getMemberId()).isEqualTo(10L);
        assertThat(principal.getRole()).isEqualTo(Role.BUYER);
        assertThat(principal.isLinked()).isFalse();
        verify(oAuthAccountService, never()).linkSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("연동용 state가 바인딩돼 있으면 linkSocialAccount로 계정을 연동하고 linked=true인 principal을 반환한다")
    void 추가_연동_성공() {
        httpServletRequest.setParameter("state", "state-2");
        when(socialLinkTicketRepository.consumeByState("state-2")).thenReturn(Optional.of(99L));
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(99L);
        when(member.getRole()).thenReturn(Role.SELLER);
        when(oAuthAccountService.linkSocialAccount(99L, "google", "google-sub-1")).thenReturn(member);

        OAuth2User result = service.loadUser(
                userRequest(Map.of("sub", "google-sub-1", "email", "user@example.com", "name", "홍길동")));

        OAuthPrincipal principal = (OAuthPrincipal) result;
        assertThat(principal.getMemberId()).isEqualTo(99L);
        assertThat(principal.isLinked()).isTrue();
        verify(oAuthAccountService, never()).findOrCreateMember(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("연동 시도 중 BusinessException이 발생하면 errorCode를 담은 OAuth2AuthenticationException으로 감싸 던진다")
    void 연동_실패시_예외를_감싸서_전파() {
        httpServletRequest.setParameter("state", "state-3");
        when(socialLinkTicketRepository.consumeByState("state-3")).thenReturn(Optional.of(99L));
        when(oAuthAccountService.linkSocialAccount(99L, "google", "google-sub-1"))
                .thenThrow(new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED));

        assertThatThrownBy(() -> service.loadUser(
                userRequest(Map.of("sub", "google-sub-1", "email", "user@example.com", "name", "홍길동"))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED.getCode()));
    }

    @Test
    @DisplayName("이메일 제공 동의가 없으면 계정 조회/생성을 시도하지 않고 email_required 예외를 던진다")
    void 이메일_없음() {
        assertThatThrownBy(() -> service.loadUser(userRequest(Map.of("sub", "google-sub-1", "name", "홍길동"))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo("email_required"));
        verify(oAuthAccountService, never()).findOrCreateMember(any(), any(), any(), any(), any());
        verify(oAuthAccountService, never()).linkSocialAccount(any(), any(), any());
    }
}
