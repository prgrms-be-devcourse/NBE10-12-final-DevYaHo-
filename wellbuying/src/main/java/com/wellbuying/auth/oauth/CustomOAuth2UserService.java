package com.wellbuying.auth.oauth;

import com.wellbuying.auth.service.OAuthAccountService;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.domain.member.entity.Member;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final OAuthAccountService oAuthAccountService;
    private final SocialLinkTicketRepository socialLinkTicketRepository;
    private final HttpServletRequest request;

    public CustomOAuth2UserService(OAuthAccountService oAuthAccountService,
            SocialLinkTicketRepository socialLinkTicketRepository, HttpServletRequest request) {
        this.oAuthAccountService = oAuthAccountService;
        this.socialLinkTicketRepository = socialLinkTicketRepository;
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
            log.warn("소셜 로그인 실패: provider={} 이메일 제공 동의 없음", provider);
            throw new OAuth2AuthenticationException(new OAuth2Error("email_required"),
                    "이메일 제공에 동의해야 로그인할 수 있습니다.");
        }

        Long linkMemberId = consumeLinkMemberId();
        try {
            if (linkMemberId != null) {
                Member member = oAuthAccountService.linkSocialAccount(linkMemberId, provider, userInfo.providerId());
                log.info("소셜 계정 연동 성공: provider={}, memberId={}", provider, member.getId());
                return new OAuthPrincipal(member.getId(), member.getRole(), attributes, true);
            }
            Member member = oAuthAccountService.findOrCreateMember(provider, userInfo.providerId(), userInfo.email(),
                    userInfo.name(), userInfo.profileImage());
            log.info("소셜 로그인 성공: provider={}, memberId={}", provider, member.getId());
            return new OAuthPrincipal(member.getId(), member.getRole(), attributes, false);
        } catch (BusinessException e) {
            log.warn("소셜 로그인 실패: provider={}, linkMemberId={}, errorCode={}", provider, linkMemberId,
                    e.getErrorCode().getCode());
            // 필터 체인 안에서는 AuthenticationException만 FailureHandler로 전달되므로 래핑해서 던짐
            throw new OAuth2AuthenticationException(new OAuth2Error(e.getErrorCode().getCode()), e.getMessage());
        }
    }

    // 이 콜백 요청의 state로 조회 - LinkAwareOAuth2AuthorizationRequestResolver가 동일 인가요청에서
    // 실제로 link_token을 소비해 바인딩해둔 경우에만 값이 존재하므로, 무관한 요청으로는 연동되지 않음
    private Long consumeLinkMemberId() {
        String state = request.getParameter("state");
        return socialLinkTicketRepository.consumeByState(state).orElse(null);
    }
}
