package com.wellbuying.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Role;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

class TokenProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long!!";

    private final TokenProvider tokenProvider =
            new TokenProvider(new JwtProperties(SECRET, 1_800_000L, 604_800_000L, 5L));

    // access token을 생성하고 파싱하면 memberId/role/deviceId가 그대로 추출되는지 검증
    @Test
    void 액세스_토큰을_생성하고_파싱하면_memberId_role_deviceId를_추출할수있다() {
        String token = tokenProvider.createAccessToken(1L, Role.BUYER, "device-1");

        Claims claims = tokenProvider.parseClaims(token);

        assertThat(tokenProvider.getMemberId(claims)).isEqualTo(1L);
        assertThat(tokenProvider.getRole(claims)).isEqualTo(Role.BUYER);
        assertThat(tokenProvider.getDeviceId(claims)).isEqualTo("device-1");
    }

    // refresh token은 role claim 없이 memberId/deviceId만 담기는지 검증
    @Test
    void 리프레시_토큰을_생성하고_파싱하면_memberId_deviceId를_추출할수있다() {
        String token = tokenProvider.createRefreshToken(1L, "device-1");

        Claims claims = tokenProvider.parseClaims(token);

        assertThat(tokenProvider.getMemberId(claims)).isEqualTo(1L);
        assertThat(tokenProvider.getDeviceId(claims)).isEqualTo("device-1");
        assertThat(claims.get("role")).isNull();
    }

    // 만료된 토큰을 파싱하면 BusinessException(TOKEN_EXPIRED)이 발생하는지 검증
    @Test
    void 만료된_토큰을_파싱하면_예외가_발생한다() {
        TokenProvider expiredTokenProvider = new TokenProvider(new JwtProperties(SECRET, -1000L, -1000L, 5L));
        String token = expiredTokenProvider.createAccessToken(1L, Role.BUYER, "device-1");

        assertThatThrownBy(() -> expiredTokenProvider.parseClaims(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    // 다른 비밀키로 서명된 토큰을 파싱하면 BusinessException(INVALID_TOKEN)이 발생하는지 검증
    @Test
    void 다른_키로_서명된_토큰을_파싱하면_예외가_발생한다() {
        TokenProvider otherTokenProvider = new TokenProvider(
                new JwtProperties("other-secret-key-must-be-at-least-256-bits!!", 1_800_000L, 604_800_000L, 5L));
        String token = otherTokenProvider.createAccessToken(1L, Role.BUYER, "device-1");

        assertThatThrownBy(() -> tokenProvider.parseClaims(token))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
