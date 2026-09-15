package com.wellbuying.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.auth.config.OAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

class OAuth2AuthenticationFailureHandlerTest {

    private static final String FAILURE_REDIRECT_URI = "https://front.example.com/oauth/error";

    private final OAuthProperties oAuthProperties = new OAuthProperties("https://front.example.com/oauth/callback",
            FAILURE_REDIRECT_URI);

    private final OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler(oAuthProperties);

    @Test
    @DisplayName("OAuth2AuthenticationException이면 그 안의 에러 코드를 error 쿼리파라미터로 실어 리다이렉트한다")
    void OAuth2_예외의_에러코드를_그대로_전달() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("email_required"), "이메일 제공에 동의해야 로그인할 수 있습니다.");

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE_REDIRECT_URI + "?error=email_required");
    }

    @Test
    @DisplayName("OAuth2AuthenticationException이 아닌 다른 인증 예외면 기본 에러 코드(oauth_login_failed)로 리다이렉트한다")
    void 그_외_예외는_기본_에러코드로_대체() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.security.authentication.BadCredentialsException exception =
                new org.springframework.security.authentication.BadCredentialsException("원인 메시지");

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE_REDIRECT_URI + "?error=oauth_login_failed");
    }
}
