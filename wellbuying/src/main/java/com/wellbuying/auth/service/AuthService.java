package com.wellbuying.auth.service;

import com.wellbuying.auth.dto.DeviceSessionResponse;
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
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.event.MemberLoginEvent;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.member.service.EmailVerificationService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final EmailVerificationService emailVerificationService;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider, RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher,
            OAuthExchangeCodeRepository oAuthExchangeCodeRepository, ApplicationEventPublisher eventPublisher,
            EmailVerificationService emailVerificationService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.oAuthExchangeCodeRepository = oAuthExchangeCodeRepository;
        this.eventPublisher = eventPublisher;
        this.emailVerificationService = emailVerificationService;
    }

    // 이메일/비밀번호 검증(소셜 전용 계정, 비밀번호 불일치 예외 처리) 후 토큰 발급하고 refresh token 해시를 Redis에 저장
    // 휴면 대상 회원은 토큰 발급 전에 차단 - 배치가 아직 처리하지 못한 대상(status=ACTIVE지만 6개월 경과)도 이 시점에 즉시 markDormant()로 전환
    // MEMBER_DORMANT 발생 시에도 markDormant()로 전환된 상태가 커밋되어야 하므로 noRollbackFor 지정
    // 주의: BusinessException 전체를 대상으로 하므로, 이 메서드에 다른 BusinessException을 새로 추가할 경우
    // 그 예외 발생 시에도 트랜잭션이 커밋된다는 점을 반드시 고려할 것
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request, String requestDeviceId) {
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (member.isSocialOnly()) {
            throw new BusinessException(ErrorCode.SOCIAL_ONLY_ACCOUNT);
        }
        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        member.validateNotDormant();

        return issueTokens(member.getId(), member.getRole(), requestDeviceId);
    }

    // 이메일 인증코드 검증 성공 시 휴면 계정을 즉시 재활성화하고 로그인 토큰까지 함께 발급
    @Transactional
    public LoginResponse reactivate(String email, String code, String requestDeviceId) {
        emailVerificationService.verifyReactivationCode(email, code);
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getStatus() != MemberStatus.DORMANT) {
            throw new BusinessException(ErrorCode.MEMBER_NOT_DORMANT);
        }
        member.reactivate();
        return issueTokens(member.getId(), member.getRole(), requestDeviceId);
    }

    // access/refresh 토큰을 발급하고 refresh token 해시를 Redis에 저장 (비밀번호 로그인/소셜 로그인 공용)
    private LoginResponse issueTokens(Long memberId, Role role, String requestDeviceId) {
        String deviceId = requestDeviceId != null ? requestDeviceId : UUID.randomUUID().toString();
        String accessToken = tokenProvider.createAccessToken(memberId, role, deviceId);
        String refreshToken = tokenProvider.createRefreshToken(memberId, deviceId);

        long now = Instant.now().getEpochSecond();
        refreshTokenRepository.save(memberId, deviceId, RefreshTokenValue.issued(tokenHasher.hash(refreshToken), now));
        eventPublisher.publishEvent(new MemberLoginEvent(memberId));

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
        eventPublisher.publishEvent(new MemberLoginEvent(memberId));

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

    // 회원의 모든 기기 로그인 세션 목록 조회 - 토큰 해시는 응답에서 제외하고 lastUsedAt 내림차순 정렬
    public List<DeviceSessionResponse> getDevices(Long memberId) {
        return refreshTokenRepository.findAll(memberId).entrySet().stream()
                .map(entry -> new DeviceSessionResponse(entry.getKey(), entry.getValue().issuedAt(),
                        entry.getValue().lastUsedAt()))
                .sorted(Comparator.comparingLong(DeviceSessionResponse::lastUsedAt).reversed())
                .toList();
    }
}
