package com.wellbuying.auth.dto;

public record ReissueResponse(String accessToken, String refreshToken, long accessTokenExpiresIn) {
}
