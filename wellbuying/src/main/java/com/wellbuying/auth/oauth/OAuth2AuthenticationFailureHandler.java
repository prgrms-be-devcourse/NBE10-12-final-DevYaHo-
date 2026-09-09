package com.wellbuying.auth.oauth;

import com.wellbuying.auth.config.OAuthProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    private static final String DEFAULT_ERROR_CODE = "oauth_login_failed";

    private final OAuthProperties oAuthProperties;

    public OAuth2AuthenticationFailureHandler(OAuthProperties oAuthProperties) {
        this.oAuthProperties = oAuthProperties;
    }

    // 예외 메시지 원문은 노출하지 않되, 우리가 이미 ErrorCode로 정의해둔 안전한 코드값은 그대로 넘겨
    // 프론트가 구체적인 실패 사유를 보여줄 수 있게 한다. 서버 로그에는 원인 전체를 남긴다.
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        log.warn("소셜 로그인 실패: {}", exception.getMessage(), exception);
        String errorCode = DEFAULT_ERROR_CODE;
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception
                && oAuth2Exception.getError().getErrorCode() != null) {
            errorCode = oAuth2Exception.getError().getErrorCode();
        }
        String redirectUri = UriComponentsBuilder.fromUriString(oAuthProperties.failureRedirectUri())
                .queryParam("error", errorCode)
                .build()
                .toUriString();
        response.sendRedirect(redirectUri);
    }
}
