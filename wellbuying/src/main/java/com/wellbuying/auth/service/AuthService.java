package com.wellbuying.auth.service;

import com.wellbuying.auth.dto.*;
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.auth.oauth.OAuthExchangeCodeRepository;
import com.wellbuying.auth.oauth.OAuthExchangePayload;
import com.wellbuying.auth.token.RefreshTokenRepository;
import com.wellbuying.auth.token.RefreshTokenValue;
import com.wellbuying.auth.token.TokenHasher;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.DormantMemberException;
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
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;
    private final OAuthExchangeCodeRepository oAuthExchangeCodeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailVerificationService emailVerificationService;
    private final TransactionTemplate transactionTemplate;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider, RefreshTokenRepository refreshTokenRepository, TokenHasher tokenHasher,
            OAuthExchangeCodeRepository oAuthExchangeCodeRepository, ApplicationEventPublisher eventPublisher,
            EmailVerificationService emailVerificationService, PlatformTransactionManager transactionManager) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenHasher = tokenHasher;
        this.oAuthExchangeCodeRepository = oAuthExchangeCodeRepository;
        this.eventPublisher = eventPublisher;
        this.emailVerificationService = emailVerificationService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // 이메일/비밀번호 검증(소셜 전용 계정, 비밀번호 불일치 예외 처리) 후 토큰 발급하고 refresh token 해시를 Redis에 저장
    // 휴면 대상 회원은 토큰 발급 전에 차단 - 배치가 아직 처리하지 못한 대상(status=ACTIVE지만 6개월 경과)도 이 시점에 즉시 markDormant()로 전환
    // DB 조회/검증(STEP1)만 짧은 트랜잭션으로 끝내 커넥션을 바로 반납하고, Redis 호출(STEP2, issueTokens())은 트랜잭션
    // 밖에서 실행한다 - Redis가 느려지거나 장애가 나도 DB 커넥션을 붙잡아두지 않기 위함(phase21 §4-3).
    // DormantMemberException 발생 시에도 markDormant()로 전환된 상태가 커밋되어야 하므로, 이 메서드 자체는 @Transactional을
    // 쓰지 않고 TransactionTemplate 콜백 안에서 이 예외를 캐치해 rollbackOnly를 걸지 않고 정상 반환(커밋)한 뒤
    // execute() 밖에서 다시 던져 @Transactional(noRollbackFor = ...)와 동일한 효과를 재현한다.
    // (다른 BusinessException 하위 타입은 대상이 아니므로 그대로 던지면 트랜잭션이 롤백된다)
    public LoginResponse login(LoginRequest request, String requestDeviceId) {
        AtomicReference<DormantMemberException> dormantException = new AtomicReference<>();
        Member member = transactionTemplate.execute(status -> {
            Member m = memberRepository.findByEmailAndDeletedAtIsNull(request.email())
                    .orElseThrow(() -> {
                        log.warn("로그인 실패: 존재하지 않는 이메일");
                        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                    });
            if (m.isSocialOnly()) {
                throw new BusinessException(ErrorCode.SOCIAL_ONLY_ACCOUNT);
            }
            if (!passwordEncoder.matches(request.password(), m.getPassword())) {
                log.warn("로그인 실패: 비밀번호 불일치 - memberId={}", m.getId());
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            try {
                m.validateNotDormant();
            } catch (DormantMemberException e) {
                dormantException.set(e);
            }
            return m;
        });
        if (dormantException.get() != null) {
            throw dormantException.get();
        }
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

    // 비밀번호 재발급 - verify 단계에서 남긴 verified 플래그를 확인·소비한 뒤 비밀번호를 교체하고,
    // 계정 탈취 가능성을 전제로 기존에 로그인되어 있던 모든 기기 세션을 무효화한다
    @Transactional
    public void resetPassword(String email, String newPassword) {
        emailVerificationService.assertPasswordReissueVerified(email);
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }
        member.changePassword(passwordEncoder.encode(newPassword));
        logoutAll(member.getId());
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

    // 소셜 로그인 성공 후 memberId/role만 1회용 교환 코드에 저장 - 토큰은 프론트의 실제 교환 요청 시점에 발급해 그때 보내는 deviceId를 재사용할 수 있게 함
    public String issueOAuthExchangeCode(Long memberId, Role role) {
        String code = UUID.randomUUID().toString();
        oAuthExchangeCodeRepository.save(code, new OAuthExchangePayload(memberId, role));
        return code;
    }

    // 교환 코드를 1회 소비해 저장된 memberId/role로 토큰을 발급 - 코드가 없거나 이미 사용됐으면 예외
    public LoginResponse exchangeOAuthCode(String code, String requestDeviceId) {
        OAuthExchangePayload payload = oAuthExchangeCodeRepository.consume(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_EXCHANGE_CODE_INVALID));
        return issueTokens(payload.memberId(), payload.role(), requestDeviceId);
    }

    // refresh token 검증 후 Lua 스크립트로 rotate하여 access/refresh 토큰을 재발급 (RTR) - role은 DB에서 최신값을 다시 조회해 반영
    // DB 조회(회원 존재 검증)와 Redis 호출(rotate)이 뒤섞여 있던 기존 readOnly 트랜잭션을 제거했다 - Redis 장애/지연 시에도
    // DB 커넥션을 붙잡아두지 않기 위함(phase21 §4-3). 폴백이 필요한 구간은 RefreshTokenFallbackStore가 자체 트랜잭션으로 처리한다.
    public ReissueResponse reissue(String refreshToken) {
        Claims claims = tokenProvider.parseClaims(refreshToken);
        Long memberId = tokenProvider.getMemberId(claims);
        String deviceId = tokenProvider.getDeviceId(claims);

        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        eventPublisher.publishEvent(new MemberLoginEvent(memberId));

        String oldTokenHash = tokenHasher.hash(refreshToken);
        String newAccessToken = tokenProvider.createAccessToken(memberId, member.getRole(), deviceId);
        String newRefreshToken = tokenProvider.createRefreshToken(memberId, deviceId);
        String newTokenHash = tokenHasher.hash(newRefreshToken);

        long result = refreshTokenRepository.rotate(memberId, deviceId, oldTokenHash, newTokenHash);
        if (result == 0) {
            log.debug("토큰 재발급 실패: 세션 없음(만료/로그아웃) - memberId={}, deviceId={}", memberId, deviceId);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }
        if (result < 0) {
            log.warn("리프레시 토큰 재사용 탐지: memberId={}, deviceId={}", memberId, deviceId);
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
