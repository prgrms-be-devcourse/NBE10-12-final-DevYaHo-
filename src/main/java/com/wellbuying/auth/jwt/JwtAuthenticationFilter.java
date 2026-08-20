package com.wellbuying.auth.jwt;

import com.wellbuying.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ERROR_CODE_ATTRIBUTE = "errorCode";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenProvider tokenProvider;

    public JwtAuthenticationFilter(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    // Authorization 헤더의 토큰을 파싱해 SecurityContext에 인증 정보를 세팅 (요청당 1회 실행)
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = tokenProvider.parseClaims(token);
                setAuthentication(claims);
            } catch (BusinessException e) {
                request.setAttribute(ERROR_CODE_ATTRIBUTE, e.getErrorCode());
            }
        }
        filterChain.doFilter(request, response);
    }

    // claims에서 memberId/deviceId/role을 추출해 SecurityContext에 인증 객체로 등록
    private void setAuthentication(Claims claims) {
        Long memberId = tokenProvider.getMemberId(claims);
        String deviceId = tokenProvider.getDeviceId(claims);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + tokenProvider.getRole(claims).name()));
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(memberId, deviceId), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // Authorization 헤더에서 "Bearer " 접두사를 제거하고 순수 토큰 문자열만 추출 (없으면 null)
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
