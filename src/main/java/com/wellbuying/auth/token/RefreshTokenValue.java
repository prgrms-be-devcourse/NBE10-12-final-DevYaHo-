package com.wellbuying.auth.token;

public record RefreshTokenValue(String tokenHash, long issuedAt, long lastUsedAt) {
}
