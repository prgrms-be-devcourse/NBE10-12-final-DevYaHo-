package com.wellbuying.auth.oauth;

import com.wellbuying.auth.config.OAuthProperties;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuthProperties oAuthProperties;

    public OAuth2AuthenticationFailureHandler(OAuthProperties oAuthProperties) {
        this.oAuthProperties = oAuthProperties;
    }

    // 실패 사유(예외 메시지)를 그대로 노출하지 않기 위해 고정된 에러 코드만 담아 프론트 실패 페이지로 리다이렉트
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String redirectUri = UriComponentsBuilder.fromUriString(oAuthProperties.failureRedirectUri())
                .queryParam("error", "oauth_login_failed")
                .build()
                .toUriString();
        response.sendRedirect(redirectUri);
    }
}
