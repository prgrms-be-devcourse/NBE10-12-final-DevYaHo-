package com.wellbuying.auth.oauth;

import com.wellbuying.auth.service.OAuthAccountService;
import com.wellbuying.member.domain.Member;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthAccountService oAuthAccountService;

    public CustomOAuth2UserService(OAuthAccountService oAuthAccountService) {
        this.oAuthAccountService = oAuthAccountService;
    }

    // provider(google/kakao)별 사용자 정보를 파싱해 계정 매칭/생성 후 인증 principal로 변환
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuthUserInfo userInfo = OAuthUserInfo.of(provider, attributes);
        Member member = oAuthAccountService.findOrCreateMember(provider, userInfo.providerId(), userInfo.email(),
                userInfo.name(), userInfo.profileImage());

        return new OAuthPrincipal(member.getId(), member.getRole(), attributes);
    }
}
