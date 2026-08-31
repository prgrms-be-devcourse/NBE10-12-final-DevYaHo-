package com.wellbuying.auth.oauth;

import com.wellbuying.domain.member.entity.Role;

// OAuth 콜백 성공 시 토큰을 바로 발급하지 않고, 실제 교환(exchange) 시점까지 대기시키는 최소 정보
// deviceId는 프론트가 교환 요청 시점에 X-Device-Id 헤더로 전달하므로 여기서는 들고 있지 않는다
public record OAuthExchangePayload(Long memberId, Role role) {
}
