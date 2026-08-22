package com.wellbuying.auth.dto;

public record DeviceSessionResponse(String deviceId, long issuedAt, long lastUsedAt) {
}
