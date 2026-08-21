package com.wellbuying.auth.jwt;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class TokenProvider {

    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final JwtProperties jwtProperties;

    public TokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    // memberId/role/deviceId를 담은 access token 발급 (만료 30분)
    public String createAccessToken(Long memberId, Role role, String deviceId) {
        return createToken(memberId, deviceId, role, jwtProperties.accessTokenExpirationMs());
    }

    // memberId/deviceId만 담은 refresh token 발급 (role claim 없음, 만료 7일)
    public String createRefreshToken(Long memberId, String deviceId) {
        return createToken(memberId, deviceId, null, jwtProperties.refreshTokenExpirationMs());
    }

    // JWT 생성 공통 로직 - subject/deviceId claim/발급·만료시각 설정 후 서명 (role은 null이면 claim 생략)
    // jti에 랜덤 UUID를 부여 - 동일 초 안에 동일 memberId+deviceId로 재발급되어도(RTR grace 경쟁 요청 등) 토큰이 같은 문자열로 겹치지 않도록 보장
    private String createToken(Long memberId, String deviceId, Role role, long expirationMs) {
        Date now = new Date();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .claim(CLAIM_DEVICE_ID, deviceId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key);
        if (role != null) {
            builder.claim(CLAIM_ROLE, role.name());
        }
        return builder.compact();
    }

    // 토큰을 검증하고 claims를 추출 - 만료 시 TOKEN_EXPIRED, 위조/서명불일치 시 INVALID_TOKEN 예외
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    // claims의 subject(memberId) 추출
    public Long getMemberId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    // claims의 deviceId claim 추출
    public String getDeviceId(Claims claims) {
        return claims.get(CLAIM_DEVICE_ID, String.class);
    }

    // claims의 role claim 추출
    public Role getRole(Claims claims) {
        return Role.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    // access token 만료 시간을 초 단위로 반환 (응답에 accessTokenExpiresIn으로 내려줌)
    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.accessTokenExpirationMs() / 1000;
    }
}
