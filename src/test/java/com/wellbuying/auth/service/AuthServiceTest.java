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
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.auth.token.RefreshTokenRepository;
import com.wellbuying.auth.token.RefreshTokenValue;
import com.wellbuying.auth.token.TokenHasher;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.Role;
import com.wellbuying.member.repository.MemberRepository;
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

    private RefreshTokenValue argThatHasHash(String hash) {
        return org.mockito.ArgumentMatchers.argThat(value -> value.tokenHash().equals(hash));
    }
}
