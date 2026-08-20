package com.wellbuying.auth.token;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefreshTokenValue(
        String tokenHash,
        String previousTokenHash,
        Long graceUntil,
        long issuedAt,
        long lastUsedAt
) {

    // 최초 로그인 시 발급 - previousTokenHash/graceUntil 없이 생성 (JSON에서 필드 자체가 생략됨)
    public static RefreshTokenValue issued(String tokenHash, long now) {
        return new RefreshTokenValue(tokenHash, null, null, now, now);
    }
}
