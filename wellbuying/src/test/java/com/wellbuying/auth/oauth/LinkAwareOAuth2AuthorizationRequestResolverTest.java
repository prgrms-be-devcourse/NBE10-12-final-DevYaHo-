package com.wellbuying.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@ExtendWith(MockitoExtension.class)
class LinkAwareOAuth2AuthorizationRequestResolverTest {

    @Mock
    private SocialLinkTicketRepository socialLinkTicketRepository;

    private LinkAwareOAuth2AuthorizationRequestResolver resolver;

    private ClientRegistrationRepository clientRegistrationRepository(ClientRegistration registration) {
        ClientRegistrationRepository repository = mock(ClientRegistrationRepository.class);
        when(repository.findByRegistrationId("google")).thenReturn(registration);
        return repository;
    }

    private ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .clientName("Google")
                .build();
    }

    private MockHttpServletRequest requestFor(String linkToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        if (linkToken != null) {
            request.setParameter("link_token", linkToken);
        }
        return request;
    }

    // link_token이 유효하면 이 인가요청의 state에 memberId가 바인딩되는지 검증 - §2-5 수정의 핵심 동작
    @Test
    void 유효한_link_token이면_인가요청의_state에_memberId를_바인딩한다() {
        ClientRegistration registration = googleRegistration();
        resolver = new LinkAwareOAuth2AuthorizationRequestResolver(clientRegistrationRepository(registration),
                socialLinkTicketRepository);
        when(socialLinkTicketRepository.consume("valid-token")).thenReturn(Optional.of(42L));

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(requestFor("valid-token"));

        assertThat(authorizationRequest).isNotNull();
        verify(socialLinkTicketRepository).bindState(eq(authorizationRequest.getState()), eq(42L));
    }

    // link_token이 없으면(일반 로그인) state 바인딩을 시도하지 않는지 검증
    @Test
    void link_token이_없으면_state를_바인딩하지_않는다() {
        ClientRegistration registration = googleRegistration();
        resolver = new LinkAwareOAuth2AuthorizationRequestResolver(clientRegistrationRepository(registration),
                socialLinkTicketRepository);

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(requestFor(null));

        assertThat(authorizationRequest).isNotNull();
        verify(socialLinkTicketRepository, never()).bindState(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    // link_token이 만료됐거나 이미 사용됐으면(consume이 empty) state를 바인딩하지 않고 일반 로그인처럼 통과시키는지 검증
    @Test
    void 만료된_link_token이면_state를_바인딩하지_않는다() {
        ClientRegistration registration = googleRegistration();
        resolver = new LinkAwareOAuth2AuthorizationRequestResolver(clientRegistrationRepository(registration),
                socialLinkTicketRepository);
        when(socialLinkTicketRepository.consume("expired-token")).thenReturn(Optional.empty());

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(requestFor("expired-token"));

        assertThat(authorizationRequest).isNotNull();
        verify(socialLinkTicketRepository, never()).bindState(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}
