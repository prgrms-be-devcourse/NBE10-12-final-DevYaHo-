package com.wellbuying.auth.service;

import com.wellbuying.auth.dto.LoginRequest;
import com.wellbuying.auth.dto.LoginResponse;
import com.wellbuying.auth.dto.ReissueRequest;
import com.wellbuying.auth.dto.ReissueResponse;
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.auth.oauth.OAuthExchangeCodeRepository;
import com.wellbuying.auth.token.RefreshTokenRepository;
import com.wellbuying.auth.token.RefreshTokenValue;
import com.wellbuying.auth.token.TokenHasher;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.Role;
import com.wellbuying.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final OAuthExchangeCodeRepository oAuthExchangeCodeRepository;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider, RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher,
            OAuthExchangeCodeRepository oAuthExchangeCodeRepository) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.oAuthExchangeCodeRepository = oAuthExchangeCodeRepository;
    }

    // 이메일/비밀번호 검증(소셜 전용 계정, 비밀번호 불일치 예외 처리) 후 토큰 발급하고 refresh token 해시를 Redis에 저장
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String requestDeviceId) {
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (member.isSocialOnly()) {
            throw new BusinessException(ErrorCode.SOCIAL_ONLY_ACCOUNT);
        }
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(member.getId(), member.getRole(), requestDeviceId);
    }

    // access/refresh 토큰을 발급하고 refresh token 해시를 Redis에 저장 (비밀번호 로그인/소셜 로그인 공용)
    private LoginResponse issueTokens(Long memberId, Role role, String requestDeviceId) {
        String deviceId = requestDeviceId != null ? requestDeviceId : UUID.randomUUID().toString();
        String accessToken = tokenProvider.createAccessToken(memberId, role, deviceId);
        String refreshToken = tokenProvider.createRefreshToken(memberId, deviceId);

        long now = Instant.now().getEpochSecond();
        refreshTokenRepository.save(memberId, deviceId, RefreshTokenValue.issued(tokenHasher.hash(refreshToken), now));

        return new LoginResponse(accessToken, refreshToken, tokenProvider.getAccessTokenExpirationSeconds(),
                deviceId);
    }

    // 소셜 로그인 성공 후 토큰을 발급해 1회용 교환 코드에 저장 - 콜백 리다이렉트 URL에 토큰이 그대로 노출되지 않도록 함
    public String issueOAuthExchangeCode(Long memberId, Role role) {
        LoginResponse loginResponse = issueTokens(memberId, role, null);
        String code = UUID.randomUUID().toString();
        oAuthExchangeCodeRepository.save(code, loginResponse);
        return code;
    }

    // 교환 코드를 1회 소비하여 저장된 토큰을 반환 - 코드가 없거나 이미 사용됐으면 예외
    public LoginResponse exchangeOAuthCode(String code) {
        return oAuthExchangeCodeRepository.consume(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_EXCHANGE_CODE_INVALID));
    }

    // refresh token 검증 후 Lua 스크립트로 rotate하여 access/refresh 토큰을 재발급 (RTR) - role은 DB에서 최신값을 다시 조회해 반영
    @Transactional(readOnly = true)
    public ReissueResponse reissue(ReissueRequest request) {
        Claims claims = tokenProvider.parseClaims(request.refreshToken());
        Long memberId = tokenProvider.getMemberId(claims);
        String deviceId = tokenProvider.getDeviceId(claims);

        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        String oldTokenHash = tokenHasher.hash(request.refreshToken());
        String newAccessToken = tokenProvider.createAccessToken(memberId, member.getRole(), deviceId);
        String newRefreshToken = tokenProvider.createRefreshToken(memberId, deviceId);
        String newTokenHash = tokenHasher.hash(newRefreshToken);

        long result = refreshTokenRepository.rotate(memberId, deviceId, oldTokenHash, newTokenHash);
        if (result == 0) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        if (result < 0) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        return new ReissueResponse(newAccessToken, newRefreshToken, tokenProvider.getAccessTokenExpirationSeconds());
    }

    // 현재 기기의 refresh token만 삭제 - 해당 기기 로그아웃
    public void logout(Long memberId, String deviceId) {
        refreshTokenRepository.delete(memberId, deviceId);
    }

    // 회원의 모든 기기 refresh token 삭제 - 전체 기기 로그아웃
    public void logoutAll(Long memberId) {
        refreshTokenRepository.deleteAll(memberId);
    }
}
