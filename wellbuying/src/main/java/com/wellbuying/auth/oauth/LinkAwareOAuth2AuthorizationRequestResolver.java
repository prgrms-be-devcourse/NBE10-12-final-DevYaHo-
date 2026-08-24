package com.wellbuying.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

// 소셜 계정 추가 연동 요청(link_token 쿼리파라미터)을 세션에 옮겨 담아 콜백 시점의 CustomOAuth2UserService가 읽을 수 있게 함
public class LinkAwareOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String SOCIAL_LINK_MEMBER_ID_SESSION_KEY = "SOCIAL_LINK_MEMBER_ID";
    private static final String LINK_TOKEN_PARAM = "link_token";
    private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final SocialLinkTicketRepository socialLinkTicketRepository;

    public LinkAwareOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
            SocialLinkTicketRepository socialLinkTicketRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
                AUTHORIZATION_REQUEST_BASE_URI);
        this.socialLinkTicketRepository = socialLinkTicketRepository;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        bindLinkMemberIdToSession(request, authorizationRequest);
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        bindLinkMemberIdToSession(request, authorizationRequest);
        return authorizationRequest;
    }

    // link_token이 없거나 이미 만료/사용됐으면 일반 로그인처럼 조용히 통과시킴
    private void bindLinkMemberIdToSession(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return;
        }
        String linkToken = request.getParameter(LINK_TOKEN_PARAM);
        if (linkToken == null) {
            return;
        }
        socialLinkTicketRepository.consume(linkToken)
                .ifPresent(memberId -> request.getSession().setAttribute(SOCIAL_LINK_MEMBER_ID_SESSION_KEY, memberId));
    }
}
