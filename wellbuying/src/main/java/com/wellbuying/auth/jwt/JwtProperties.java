package com.wellbuying.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpirationMs,
        long refreshTokenExpirationMs,
        long refreshTokenGraceSeconds,
        // 로컬은 http라 쿠키에 Secure를 걸면 브라우저가 저장/전송을 거부한다 - application-local.yaml에서 false로 오버라이드
        boolean cookieSecure
) {
}
