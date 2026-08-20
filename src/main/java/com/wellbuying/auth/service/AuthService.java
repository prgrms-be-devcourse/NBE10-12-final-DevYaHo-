package com.wellbuying.auth.service;

import com.wellbuying.auth.dto.LoginRequest;
import com.wellbuying.auth.dto.LoginResponse;
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.auth.token.RefreshTokenRepository;
import com.wellbuying.auth.token.RefreshTokenValue;
import com.wellbuying.auth.token.TokenHasher;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.repository.MemberRepository;
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

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider, RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
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

        String deviceId = requestDeviceId != null ? requestDeviceId : UUID.randomUUID().toString();
        String accessToken = tokenProvider.createAccessToken(member.getId(), member.getRole(), deviceId);
        String refreshToken = tokenProvider.createRefreshToken(member.getId(), deviceId);

        long now = Instant.now().getEpochSecond();
        refreshTokenRepository.save(member.getId(), deviceId,
                new RefreshTokenValue(tokenHasher.hash(refreshToken), now, now));

        return new LoginResponse(accessToken, refreshToken, tokenProvider.getAccessTokenExpirationSeconds(),
                deviceId);
    }
}
