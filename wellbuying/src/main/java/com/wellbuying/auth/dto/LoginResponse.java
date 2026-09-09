package com.wellbuying.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

// refreshToken은 JSON 응답에는 노출하지 않고 AuthController가 httpOnly 쿠키를 만들 때만 사용한다
public record LoginResponse(String accessToken, @JsonIgnore String refreshToken, long accessTokenExpiresIn,
        String deviceId) {
}
