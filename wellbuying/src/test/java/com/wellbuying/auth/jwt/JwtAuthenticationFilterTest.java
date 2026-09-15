package com.wellbuying.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private TokenProvider tokenProvider;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(tokenProvider);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 SecurityContext에 인증을 세팅하지 않고 다음 필터로 계속 진행한다")
    void 헤더_없음() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE)).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("토큰 파싱에 실패하면 request 속성에 errorCode를 담고, 예외를 밖으로 전파하지 않은 채 다음 필터로 계속 진행한다")
    void 토큰_파싱_실패() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        when(tokenProvider.parseClaims("invalid-token"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE)).isEqualTo(ErrorCode.INVALID_TOKEN);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효한 토큰이면 SecurityContext에 memberId/deviceId/role이 올바르게 세팅된다")
    void 유효한_토큰() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        Claims claims = mock(Claims.class);
        when(tokenProvider.parseClaims("valid-token")).thenReturn(claims);
        when(tokenProvider.getMemberId(claims)).thenReturn(42L);
        when(tokenProvider.getDeviceId(claims)).thenReturn("device-abc");
        when(tokenProvider.getRole(claims)).thenReturn(Role.BUYER);

        filter.doFilter(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedMember(42L, "device-abc"));
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_BUYER");
        assertThat(request.getAttribute(JwtAuthenticationFilter.ERROR_CODE_ATTRIBUTE)).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
