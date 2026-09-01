package com.wellbuying.domain.member.event;

public record ReactivationCodeIssuedEvent(String email, String content) {
}
