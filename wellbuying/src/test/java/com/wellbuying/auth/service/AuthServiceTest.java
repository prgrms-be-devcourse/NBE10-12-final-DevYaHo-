package com.wellbuying.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wellbuying.auth.dto.DeviceSessionResponse;
import com.wellbuying.auth.dto.LoginRequest;
import com.wellbuying.auth.dto.LoginResponse;
import com.wellbuying.auth.oauth.OAuthExchangeCodeRepository;
import com.wellbuying.auth.oauth.OAuthExchangePayload;
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.auth.token.RefreshTokenRepository;
import com.wellbuying.auth.token.RefreshTokenValue;
import com.wellbuying.auth.token.TokenHasher;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.DormantMemberException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.auth.dto.ReissueRequest;
import com.wellbuying.auth.dto.ReissueResponse;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.member.service.EmailVerificationService;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private OAuthExchangeCodeRepository oAuthExchangeCodeRepository;

    @Mock
    private Claims claims;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    // 이메일/비밀번호가 일치하면 access/refresh 토큰을 발급하고 refresh token 해시를 Redis에 저장하는지 검증
    @Test
    void 이메일과_비밀번호가_일치하면_토큰을_발급하고_리프레시토큰을_저장한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Pass1234!", "encoded-password")).thenReturn(true);
        when(tokenProvider.createAccessToken(any(), eq(Role.BUYER), eq("device-1"))).thenReturn("access-token");
        when(tokenProvider.createRefreshToken(any(), eq("device-1"))).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        LoginResponse response = authService.login(new LoginRequest("test@example.com", "Pass1234!"), "device-1");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.accessTokenExpiresIn()).isEqualTo(1800L);
        assertThat(response.deviceId()).isEqualTo("device-1");
        verify(refreshTokenRepository).save(isNull(), eq("device-1"), argThatHasHash("hashed-refresh-token"));
    }

    // 요청에 X-Device-Id가 없으면 서버가 UUID 형식의 deviceId를 새로 발급하는지 검증
    @Test
    void deviceId가_없으면_새로_발급한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.createAccessToken(any(), any(), anyString())).thenReturn("access-token");
        when(tokenProvider.createRefreshToken(any(), anyString())).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(tokenHasher.hash(anyString())).thenReturn("hashed-refresh-token");

        LoginResponse response = authService.login(new LoginRequest("test@example.com", "Pass1234!"), null);

        assertThat(response.deviceId()).isNotBlank();
        assertThat(UUID.fromString(response.deviceId())).isNotNull();
    }

    // 가입되지 않은 이메일로 로그인 시 INVALID_CREDENTIALS 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_이메일로_로그인하면_예외가_발생한다() {
        when(memberRepository.findByEmailAndDeletedAtIsNull("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("none@example.com", "Pass1234!"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    // 비밀번호가 일치하지 않으면 INVALID_CREDENTIALS 예외가 발생하고 토큰이 발급되지 않는지 검증
    @Test
    void 비밀번호가_일치하지_않으면_예외가_발생한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("test@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("WrongPassword!", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("test@example.com", "WrongPassword!"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(tokenProvider, never()).createAccessToken(anyLong(), any(), anyString());
    }

    // 비밀번호가 없는(소셜 전용) 계정으로 로그인 시 SOCIAL_ONLY_ACCOUNT 예외가 발생하고 비밀번호 비교를 시도하지 않는지 검증
    @Test
    void 소셜전용_계정은_로그인시_예외가_발생한다() {
        Member member = Member.socialOnly("social@example.com", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("social@example.com")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.login(new LoginRequest("social@example.com", "Pass1234!"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SOCIAL_ONLY_ACCOUNT);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    // 이미 DORMANT 상태인 회원이 로그인을 시도하면 토큰 발급 없이 MEMBER_DORMANT 예외가 발생하는지 검증
    @Test
    void DORMANT_상태의_회원은_로그인시_예외가_발생한다() {
        Member member = Member.signUp("dormant@example.com", "encoded-password", "홍길동");
        member.markDormant();
        when(memberRepository.findByEmailAndDeletedAtIsNull("dormant@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Pass1234!", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("dormant@example.com", "Pass1234!"), null))
                .isInstanceOf(DormantMemberException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_DORMANT);
        verify(tokenProvider, never()).createAccessToken(anyLong(), any(), anyString());
    }

    // 배치가 아직 처리하지 못한 휴면 대상(status=ACTIVE, 마지막 로그인 6개월 경과) 회원은 로그인 시도 시 그 자리에서 DORMANT로 전환되고 차단되는지 검증
    @Test
    void 휴면_전환_대상_회원은_로그인시_DORMANT로_전환되며_예외가_발생한다() {
        Member member = Member.signUp("eligible@example.com", "encoded-password", "홍길동");
        ReflectionTestUtils.setField(member, "lastLoginAt", LocalDateTime.now().minusMonths(7));
        when(memberRepository.findByEmailAndDeletedAtIsNull("eligible@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("Pass1234!", "encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("eligible@example.com", "Pass1234!"), null))
                .isInstanceOf(DormantMemberException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_DORMANT);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.DORMANT);
    }

    // 재활성화 코드 검증 성공 시 회원이 ACTIVE로 전환되고 로그인 토큰까지 발급되는지 검증
    @Test
    void 재활성화_코드_검증에_성공하면_ACTIVE로_전환되고_로그인토큰을_발급한다() {
        Member member = Member.signUp("dormant@example.com", "encoded-password", "홍길동");
        member.markDormant();
        when(memberRepository.findByEmailAndDeletedAtIsNull("dormant@example.com")).thenReturn(Optional.of(member));
        when(tokenProvider.createAccessToken(any(), eq(Role.BUYER), eq("device-1"))).thenReturn("access-token");
        when(tokenProvider.createRefreshToken(any(), eq("device-1"))).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        LoginResponse response = authService.reactivate("dormant@example.com", "482913", "device-1");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.deviceId()).isEqualTo("device-1");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        verify(emailVerificationService).verifyReactivationCode("dormant@example.com", "482913");
    }

    // 인증코드 검증에 실패하면 재활성화가 진행되지 않는지 검증 (EmailVerificationService가 던지는 예외를 그대로 전파)
    @Test
    void 재활성화_코드가_유효하지_않으면_예외가_발생한다() {
        doThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID))
                .when(emailVerificationService).verifyReactivationCode("dormant@example.com", "000000");

        assertThatThrownBy(() -> authService.reactivate("dormant@example.com", "000000", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        verify(memberRepository, never()).findByEmailAndDeletedAtIsNull(anyString());
    }

    // 코드는 유효하지만 이미 ACTIVE로 돌아온 회원이면 MEMBER_NOT_DORMANT 예외가 발생하는지 검증
    @Test
    void 이미_ACTIVE인_회원의_재활성화_요청은_실패한다() {
        Member member = Member.signUp("active@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("active@example.com")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> authService.reactivate("active@example.com", "482913", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_DORMANT);
        verify(tokenProvider, never()).createAccessToken(anyLong(), any(), anyString());
    }

    // 비밀번호 재설정에 성공하면 새 비밀번호로 교체되고 전체 기기 세션이 무효화되는지 검증
    @Test
    void 비밀번호_재설정에_성공하면_비밀번호가_교체되고_전체_세션이_무효화된다() {
        Member member = Member.signUp("reissue@example.com", "old-encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("reissue@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.encode("NewPass1234!")).thenReturn("new-encoded-password");

        authService.resetPassword("reissue@example.com", "NewPass1234!");

        assertThat(member.getPassword()).isEqualTo("new-encoded-password");
        verify(emailVerificationService).assertPasswordReissueVerified("reissue@example.com");
        verify(refreshTokenRepository).deleteAll(member.getId());
    }

    // 새 비밀번호가 기존 비밀번호와 동일하면 PASSWORD_SAME_AS_OLD 예외가 발생하고 비밀번호도 바뀌지 않는지 검증
    @Test
    void 기존_비밀번호와_동일하면_비밀번호_재설정이_실패한다() {
        Member member = Member.signUp("reissue-same@example.com", "old-encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("reissue-same@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("OldPass1234!", "old-encoded-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.resetPassword("reissue-same@example.com", "OldPass1234!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_SAME_AS_OLD);
        assertThat(member.getPassword()).isEqualTo("old-encoded-password");
        verify(refreshTokenRepository, never()).deleteAll(anyLong());
    }

    // 검증(verify) 단계를 거치지 않으면 비밀번호 재설정이 거부되고 비밀번호도 바뀌지 않는지 검증
    @Test
    void 검증을_거치지_않으면_비밀번호_재설정이_실패한다() {
        doThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(emailVerificationService).assertPasswordReissueVerified("reissue@example.com");

        assertThatThrownBy(() -> authService.resetPassword("reissue@example.com", "NewPass1234!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        verify(memberRepository, never()).findByEmailAndDeletedAtIsNull(anyString());
        verify(refreshTokenRepository, never()).deleteAll(anyLong());
    }

    // 유효한 refresh token으로 재발급 요청 시 새 access/refresh 토큰을 발급하고 rotate를 호출하는지 검증
    @Test
    void 유효한_refresh_token으로_재발급하면_새_토큰을_발급하고_rotate를_호출한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(tokenProvider.parseClaims("old-refresh-token")).thenReturn(claims);
        when(tokenProvider.getMemberId(claims)).thenReturn(1L);
        when(tokenProvider.getDeviceId(claims)).thenReturn("device-1");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(tokenProvider.createAccessToken(1L, Role.BUYER, "device-1")).thenReturn("new-access-token");
        when(tokenProvider.createRefreshToken(1L, "device-1")).thenReturn("new-refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(tokenHasher.hash("old-refresh-token")).thenReturn("old-hash");
        when(tokenHasher.hash("new-refresh-token")).thenReturn("new-hash");
        when(refreshTokenRepository.rotate(1L, "device-1", "old-hash", "new-hash")).thenReturn(1L);

        ReissueResponse response = authService.reissue(new ReissueRequest("old-refresh-token"));

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(response.accessTokenExpiresIn()).isEqualTo(1800L);
    }

    // 탈퇴했거나 존재하지 않는 회원의 refresh token으로 재발급 시 MEMBER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_회원의_refresh_token이면_예외가_발생한다() {
        when(tokenProvider.parseClaims("old-refresh-token")).thenReturn(claims);
        when(tokenProvider.getMemberId(claims)).thenReturn(1L);
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(new ReissueRequest("old-refresh-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    // rotate 결과가 0(세션 없음)이면 REFRESH_TOKEN_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 세션이_없으면_재발급에_실패한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(tokenProvider.parseClaims("old-refresh-token")).thenReturn(claims);
        when(tokenProvider.getMemberId(claims)).thenReturn(1L);
        when(tokenProvider.getDeviceId(claims)).thenReturn("device-1");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(tokenProvider.createAccessToken(any(), any(), anyString())).thenReturn("new-access-token");
        when(tokenProvider.createRefreshToken(any(), anyString())).thenReturn("new-refresh-token");
        when(tokenHasher.hash(anyString())).thenReturn("some-hash");
        when(refreshTokenRepository.rotate(anyLong(), anyString(), anyString(), anyString())).thenReturn(0L);

        assertThatThrownBy(() -> authService.reissue(new ReissueRequest("old-refresh-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    // rotate 결과가 음수(재사용 감지)이면 REFRESH_TOKEN_REUSE_DETECTED 예외가 발생하는지 검증
    @Test
    void 토큰_재사용이_감지되면_재발급에_실패한다() {
        Member member = Member.signUp("test@example.com", "encoded-password", "홍길동");
        when(tokenProvider.parseClaims("old-refresh-token")).thenReturn(claims);
        when(tokenProvider.getMemberId(claims)).thenReturn(1L);
        when(tokenProvider.getDeviceId(claims)).thenReturn("device-1");
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(member));
        when(tokenProvider.createAccessToken(any(), any(), anyString())).thenReturn("new-access-token");
        when(tokenProvider.createRefreshToken(any(), anyString())).thenReturn("new-refresh-token");
        when(tokenHasher.hash(anyString())).thenReturn("some-hash");
        when(refreshTokenRepository.rotate(anyLong(), anyString(), anyString(), anyString())).thenReturn(-1L);

        assertThatThrownBy(() -> authService.reissue(new ReissueRequest("old-refresh-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
    }

    // 로그아웃 시 해당 기기의 refresh token만 삭제하는지 검증
    @Test
    void 로그아웃하면_해당_기기의_refresh_token을_삭제한다() {
        authService.logout(1L, "device-1");

        verify(refreshTokenRepository).delete(1L, "device-1");
    }

    // 전체 로그아웃 시 회원의 모든 기기 refresh token을 삭제하는지 검증
    @Test
    void 전체_로그아웃하면_모든_기기의_refresh_token을_삭제한다() {
        authService.logoutAll(1L);

        verify(refreshTokenRepository).deleteAll(1L);
    }

    // 소셜 로그인 성공 시에는 토큰을 발급하지 않고 memberId/role만 1회용 교환 코드에 저장하는지 검증
    @Test
    void 소셜_로그인_성공시_토큰_발급없이_memberId와_role만_교환코드에_저장한다() {
        String code = authService.issueOAuthExchangeCode(1L, Role.BUYER);

        assertThat(code).isNotBlank();
        verify(oAuthExchangeCodeRepository).save(eq(code), eq(new OAuthExchangePayload(1L, Role.BUYER)));
        verifyNoInteractions(tokenProvider, refreshTokenRepository);
    }

    // 유효한 교환 코드로 요청하면 저장된 memberId/role로 토큰을 발급하고, 요청에 실린 deviceId를 그대로 재사용하는지 검증
    @Test
    void 유효한_교환코드로_기존_deviceId를_재사용해_토큰을_발급한다() {
        when(oAuthExchangeCodeRepository.consume("valid-code"))
                .thenReturn(Optional.of(new OAuthExchangePayload(1L, Role.BUYER)));
        when(tokenProvider.createAccessToken(eq(1L), eq(Role.BUYER), eq("device-1"))).thenReturn("access-token");
        when(tokenProvider.createRefreshToken(eq(1L), eq("device-1"))).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        LoginResponse response = authService.exchangeOAuthCode("valid-code", "device-1");

        assertThat(response.deviceId()).isEqualTo("device-1");
        verify(refreshTokenRepository).save(eq(1L), eq("device-1"), argThatHasHash("hashed-refresh-token"));
    }

    // 요청에 deviceId가 없으면 서버가 새로 발급하는지 검증
    @Test
    void 유효한_교환코드에_deviceId가_없으면_새로_발급한다() {
        when(oAuthExchangeCodeRepository.consume("valid-code"))
                .thenReturn(Optional.of(new OAuthExchangePayload(1L, Role.BUYER)));
        when(tokenProvider.createAccessToken(eq(1L), eq(Role.BUYER), anyString())).thenReturn("access-token");
        when(tokenProvider.createRefreshToken(eq(1L), anyString())).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        LoginResponse response = authService.exchangeOAuthCode("valid-code", null);

        assertThat(response.deviceId()).isNotBlank();
        assertThat(UUID.fromString(response.deviceId())).isNotNull();
    }

    // 존재하지 않거나 이미 사용된 교환 코드로 요청하면 OAUTH_EXCHANGE_CODE_INVALID 예외가 발생하는지 검증
    @Test
    void 유효하지_않은_교환코드면_예외가_발생한다() {
        when(oAuthExchangeCodeRepository.consume("invalid-code")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.exchangeOAuthCode("invalid-code", "device-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_EXCHANGE_CODE_INVALID);
    }

    // 기기 목록 조회 시 토큰 해시는 응답에서 제외하고 lastUsedAt 내림차순으로 정렬해 반환하는지 검증
    @Test
    void 기기_목록을_lastUsedAt_내림차순으로_조회한다() {
        Map<String, RefreshTokenValue> stored = new LinkedHashMap<>();
        stored.put("device-1", RefreshTokenValue.issued("hash-1", 100L));
        stored.put("device-2", RefreshTokenValue.issued("hash-2", 200L));
        when(refreshTokenRepository.findAll(1L)).thenReturn(stored);

        List<DeviceSessionResponse> response = authService.getDevices(1L);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).deviceId()).isEqualTo("device-2");
        assertThat(response.get(1).deviceId()).isEqualTo("device-1");
    }

    // 로그인 세션이 없으면 빈 목록을 반환하는지 검증
    @Test
    void 로그인된_기기가_없으면_빈_목록을_반환한다() {
        when(refreshTokenRepository.findAll(1L)).thenReturn(Map.of());

        List<DeviceSessionResponse> response = authService.getDevices(1L);

        assertThat(response).isEmpty();
    }

    private RefreshTokenValue argThatHasHash(String hash) {
        return org.mockito.ArgumentMatchers.argThat(value -> value.tokenHash().equals(hash));
    }
}
