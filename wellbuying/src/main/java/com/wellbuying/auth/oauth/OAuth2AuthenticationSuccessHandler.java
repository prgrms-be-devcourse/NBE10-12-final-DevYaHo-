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
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        OAuthPrincipal principal = (OAuthPrincipal) authentication.getPrincipal();
        String code = authService.issueOAuthExchangeCode(principal.getMemberId(), principal.getRole());

        String redirectUri = UriComponentsBuilder.fromUriString(oAuthProperties.successRedirectUri())
                .queryParam("code", code)
                .build()
                .toUriString();
        response.sendRedirect(redirectUri);
    }
}
