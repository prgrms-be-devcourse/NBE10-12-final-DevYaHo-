package com.wellbuying.domain.member.event;

public record VerificationCodeIssuedEvent(String email, String content) {
}
