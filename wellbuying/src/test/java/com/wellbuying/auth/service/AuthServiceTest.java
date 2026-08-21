package com.wellbuying.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.auth.dto.LoginRequest;
import com.wellbuying.auth.dto.LoginResponse;
import com.wellbuying.auth.oauth.OAuthExchangeCodeRepository;
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.auth.token.RefreshTokenRepository;
import com.wellbuying.auth.token.RefreshTokenValue;
import com.wellbuying.auth.token.TokenHasher;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.auth.dto.ReissueRequest;
import com.wellbuying.auth.dto.ReissueResponse;
import com.wellbuying.member.domain.Role;
import com.wellbuying.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    // 소셜 로그인 성공 시 토큰을 발급해 Redis에 refresh token을 저장하고, 발급한 토큰을 1회용 교환 코드에 저장하는지 검증
    @Test
    void 소셜_로그인_성공시_토큰을_발급하고_교환코드를_저장한다() {
        when(tokenProvider.createAccessToken(eq(1L), eq(Role.BUYER), anyString())).thenReturn("access-token");
        when(tokenProvider.createRefreshToken(eq(1L), anyString())).thenReturn("refresh-token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(1800L);
        when(tokenHasher.hash("refresh-token")).thenReturn("hashed-refresh-token");

        String code = authService.issueOAuthExchangeCode(1L, Role.BUYER);

        assertThat(code).isNotBlank();
        verify(refreshTokenRepository).save(eq(1L), anyString(), argThatHasHash("hashed-refresh-token"));
        verify(oAuthExchangeCodeRepository).save(eq(code), any(LoginResponse.class));
    }

    // 유효한 교환 코드로 요청하면 저장된 토큰을 그대로 반환하는지 검증
    @Test
    void 유효한_교환코드로_토큰을_반환한다() {
        LoginResponse loginResponse = new LoginResponse("access-token", "refresh-token", 1800L, "device-1");
        when(oAuthExchangeCodeRepository.consume("valid-code")).thenReturn(Optional.of(loginResponse));

        LoginResponse response = authService.exchangeOAuthCode("valid-code");

        assertThat(response).isEqualTo(loginResponse);
    }

    // 존재하지 않거나 이미 사용된 교환 코드로 요청하면 OAUTH_EXCHANGE_CODE_INVALID 예외가 발생하는지 검증
    @Test
    void 유효하지_않은_교환코드면_예외가_발생한다() {
        when(oAuthExchangeCodeRepository.consume("invalid-code")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.exchangeOAuthCode("invalid-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_EXCHANGE_CODE_INVALID);
    }

    private RefreshTokenValue argThatHasHash(String hash) {
        return org.mockito.ArgumentMatchers.argThat(value -> value.tokenHash().equals(hash));
    }
}
