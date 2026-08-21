package com.wellbuying.auth.oauth;

import com.wellbuying.auth.config.OAuthProperties;
import com.wellbuying.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
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
            String provider = extractProvider(request);
            redirectUriBuilder.queryParam("linked", true)
                    .queryParam("provider", provider);
        } else {
            String code = authService.issueOAuthExchangeCode(principal.getMemberId(), principal.getRole());
            redirectUriBuilder.queryParam("code", code);
        }

        response.sendRedirect(redirectUriBuilder.build().toUriString());
    }

    // 콜백 URI(/login/oauth2/code/{provider})의 마지막 경로 세그먼트에서 provider를 추출
    private String extractProvider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.substring(uri.lastIndexOf('/') + 1);
    }
}
