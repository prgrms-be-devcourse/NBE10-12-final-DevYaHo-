package com.wellbuying.auth.oauth;

import com.wellbuying.auth.service.OAuthAccountService;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.member.domain.Member;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthAccountService oAuthAccountService;
    private final HttpServletRequest request;

    public CustomOAuth2UserService(OAuthAccountService oAuthAccountService, HttpServletRequest request) {
        this.oAuthAccountService = oAuthAccountService;
        this.request = request;
    }

    // provider(google/kakao)별 사용자 정보를 파싱해 계정 매칭/생성(또는 로그인 상태에서의 추가 연동) 후 인증 principal로 변환
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuthUserInfo userInfo = OAuthUserInfo.of(provider, attributes);
        if (userInfo.email() == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_required"),
                    "이메일 제공에 동의해야 로그인할 수 있습니다.");
        }

        Long linkMemberId = consumeLinkMemberId();
        try {
            if (linkMemberId != null) {
                Member member = oAuthAccountService.linkSocialAccount(linkMemberId, provider, userInfo.providerId());
                return new OAuthPrincipal(member.getId(), member.getRole(), attributes, true);
            }
            Member member = oAuthAccountService.findOrCreateMember(provider, userInfo.providerId(), userInfo.email(),
                    userInfo.name(), userInfo.profileImage());
            return new OAuthPrincipal(member.getId(), member.getRole(), attributes, false);
        } catch (BusinessException e) {
            // 필터 체인 안에서는 AuthenticationException만 FailureHandler로 전달되므로 래핑해서 던짐
            throw new OAuth2AuthenticationException(new OAuth2Error(e.getErrorCode().getCode()), e.getMessage());
        }
    }

    // 성공/실패 관계없이 세션에 남지 않도록 읽는 즉시 제거
    private Long consumeLinkMemberId() {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Long linkMemberId = (Long) session.getAttribute(
                LinkAwareOAuth2AuthorizationRequestResolver.SOCIAL_LINK_MEMBER_ID_SESSION_KEY);
        session.removeAttribute(LinkAwareOAuth2AuthorizationRequestResolver.SOCIAL_LINK_MEMBER_ID_SESSION_KEY);
        return linkMemberId;
    }
}
