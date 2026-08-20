package com.wellbuying.auth.jwt;

public record AuthenticatedMember(Long memberId, String deviceId) {
}
