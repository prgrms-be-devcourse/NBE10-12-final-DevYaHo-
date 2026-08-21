package com.wellbuying.auth.oauth;

import com.wellbuying.auth.config.OAuthProperties;
import com.wellbuying.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final OAuthProperties oAuthProperties;

    public OAuth2AuthenticationSuccessHandler(AuthService authService, OAuthProperties oAuthProperties) {
        this.authService = authService;
        this.oAuthProperties = oAuthProperties;
    }

    // 콜백 리다이렉트 URL에 토큰을 직접 노출하지 않기 위해 1회용 교환 코드를 발급해 프론트로 리다이렉트
    // 로그인 상태에서의 추가 연동이면 기존 토큰이 그대로 유효하므로 교환 코드 없이 결과만 리다이렉트
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuthPrincipal principal = (OAuthPrincipal) authentication.getPrincipal();

        UriComponentsBuilder redirectUriBuilder = UriComponentsBuilder.fromUriString(
                oAuthProperties.successRedirectUri());
        if (principal.isLinked()) {
            String provider = extractProvider(authentication);
            redirectUriBuilder.queryParam("linked", true)
                    .queryParam("provider", provider);
        } else {
            String code = authService.issueOAuthExchangeCode(principal.getMemberId(), principal.getRole());
            redirectUriBuilder.queryParam("code", code);
        }

        response.sendRedirect(redirectUriBuilder.build().toUriString());
    }

    // URI 문자열 파싱 대신 OAuth2AuthenticationToken이 들고 있는 registrationId를 그대로 사용
    private String extractProvider(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oAuth2AuthenticationToken) {
            return oAuth2AuthenticationToken.getAuthorizedClientRegistrationId();
        }
        throw new IllegalArgumentException("Unsupported authentication type: " + authentication.getClass());
    }
}
