package com.wellbuying.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, long accessTokenExpiresIn, String deviceId) {
}
