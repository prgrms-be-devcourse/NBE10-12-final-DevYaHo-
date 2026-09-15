package com.wellbuying.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.auth.config.OAuthProperties;
import com.wellbuying.auth.service.AuthService;
import com.wellbuying.domain.member.entity.Role;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    private static final String SUCCESS_REDIRECT_URI = "https://front.example.com/oauth/callback";

    @Mock
    private AuthService authService;

    private final OAuthProperties oAuthProperties = new OAuthProperties(SUCCESS_REDIRECT_URI, "https://front.example.com/oauth/error");

    private OAuth2AuthenticationSuccessHandler handler() {
        return new OAuth2AuthenticationSuccessHandler(authService, oAuthProperties);
    }

    private OAuth2AuthenticationToken token(OAuthPrincipal principal, String registrationId) {
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), registrationId);
    }

    @Test
    @DisplayName("신규 로그인(연동 아님)이면 교환 코드를 발급해 code 쿼리파라미터로 리다이렉트한다")
    void 신규_로그인() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal(1L, Role.BUYER, Map.of("sub", "google-1"), false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authService.issueOAuthExchangeCode(1L, Role.BUYER)).thenReturn("exchange-code-123");

        handler().onAuthenticationSuccess(request, response, token(principal, "google"));

        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT_URI + "?code=exchange-code-123");
    }

    @Test
    @DisplayName("로그인 상태에서의 추가 연동이면 교환 코드를 발급하지 않고 linked/provider 쿼리파라미터로 리다이렉트한다")
    void 추가_연동() throws Exception {
        OAuthPrincipal principal = new OAuthPrincipal(1L, Role.BUYER, Map.of("sub", "google-1"), true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler().onAuthenticationSuccess(request, response, token(principal, "google"));

        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT_URI + "?linked=true&provider=GOOGLE");
        verify(authService, never()).issueOAuthExchangeCode(eq(1L), org.mockito.ArgumentMatchers.any());
    }
}
