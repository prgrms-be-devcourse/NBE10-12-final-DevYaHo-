package com.wellbuying.domain.member.event;

public record PasswordReissueCodeIssuedEvent(String email, String content) {
}
