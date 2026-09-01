package com.wellbuying.domain.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.event.ReactivationCodeIssuedEvent;
import com.wellbuying.domain.member.mail.EmailCooldownGuard;
import com.wellbuying.domain.member.mail.MailService;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private MailService mailService;

    @Mock
    private EmailCooldownGuard emailCooldownGuard;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    // 코드 발송 시 6자리 코드가 email:verification:{email} 키로 5분 TTL로 저장되는지 검증
    @Test
    void 인증코드를_발송하면_Redis에_5분_TTL로_저장된다() {
        when(memberRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        emailVerificationService.sendVerificationCode("test@example.com");

        verify(valueOperations).set(eq("email:verification:test@example.com"), anyString(),
                eq(Duration.ofMinutes(5)));
        verify(emailCooldownGuard).acquire(eq("verification"), eq("test@example.com"), eq(30L));
        verify(mailService).sendHtmlEmail(eq("test@example.com"), anyString(), anyString());
    }

    // existsByEmail이 true인 이메일로 발송 요청 시 예외가 발생하고 메일이 발송되지 않는지 검증
    @Test
    void 이미_가입된_이메일이면_인증코드_발송이_거부된다() {
        when(memberRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode("duplicate@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        verify(mailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    // 직전 발송 후 30초 이내 재요청 시 쿨다운 예외가 발생하는지 검증
    @Test
    void 쿨다운_중에_재발송을_요청하면_예외가_발생한다() {
        when(memberRepository.existsByEmail("cooldown@example.com")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.EMAIL_VERIFICATION_COOLDOWN))
                .when(emailCooldownGuard).acquire("verification", "cooldown@example.com", 30L);

        assertThatThrownBy(() -> emailVerificationService.sendVerificationCode("cooldown@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_COOLDOWN);
        verify(mailService, never()).sendHtmlEmail(anyString(), anyString(), anyString());
    }

    // 저장된 코드와 일치하는 코드로 검증 시 코드는 삭제되고 email:verified:{email} 플래그가 30분 TTL로 저장되는지 검증
    @Test
    void 올바른_코드로_검증하면_인증완료_플래그가_저장된다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email:verification:test@example.com")).thenReturn("482913");

        emailVerificationService.verifyCode("test@example.com", "482913");

        verify(redisTemplate).delete("email:verification:test@example.com");
        verify(valueOperations).set("email:verified:test@example.com", "1", Duration.ofMinutes(30));
    }

    // 저장된 코드와 다른 코드로 검증 시 예외가 발생하는지 검증
    @Test
    void 코드가_일치하지_않으면_검증에_실패한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email:verification:test@example.com")).thenReturn("482913");

        assertThatThrownBy(() -> emailVerificationService.verifyCode("test@example.com", "000000"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        verify(redisTemplate, never()).delete(anyString());
    }

    // Redis에 코드가 없는(만료/미발송) 상태로 검증 시 예외가 발생하는지 검증
    @Test
    void 코드가_만료되었으면_검증에_실패한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email:verification:test@example.com")).thenReturn(null);

        assertThatThrownBy(() -> emailVerificationService.verifyCode("test@example.com", "482913"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
    }

    // signUp 진입 가드 - 플래그가 있으면 통과 후 플래그를 삭제하는지 검증
    @Test
    void 인증완료_플래그가_있으면_assertVerified가_통과하고_플래그를_소비한다() {
        when(redisTemplate.delete("email:verified:test@example.com")).thenReturn(true);

        emailVerificationService.assertVerified("test@example.com");

        verify(redisTemplate, times(1)).delete("email:verified:test@example.com");
    }

    // 휴면 회원에게 재활성화 코드를 발송하면 email:reactivation:{email} 키로 5분 TTL로 저장되는지 검증
    @Test
    void 휴면_회원에게_재활성화_코드를_발송하면_Redis에_저장된다() {
        Member member = Member.signUp("dormant@example.com", "encoded-password", "홍길동");
        member.markDormant();
        when(memberRepository.findByEmailAndDeletedAtIsNull("dormant@example.com")).thenReturn(Optional.of(member));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        emailVerificationService.sendReactivationCode("dormant@example.com");

        verify(valueOperations).set(eq("email:reactivation:dormant@example.com"), anyString(),
                eq(Duration.ofMinutes(5)));
        verify(emailCooldownGuard).acquire(eq("reactivation"), eq("dormant@example.com"), eq(30L));
        verify(eventPublisher).publishEvent(any(ReactivationCodeIssuedEvent.class));
    }

    // 존재하지 않는 이메일로 재활성화 코드 요청 시 MEMBER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void 존재하지_않는_회원의_재활성화_코드_요청은_실패한다() {
        when(memberRepository.findByEmailAndDeletedAtIsNull("none@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.sendReactivationCode("none@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 휴면 상태가 아닌 회원이 재활성화 코드를 요청하면 MEMBER_NOT_DORMANT 예외가 발생하고, 쿨다운도 소모되지 않는지 검증
    @Test
    void 휴면이_아닌_회원의_재활성화_코드_요청은_실패한다() {
        Member member = Member.signUp("active@example.com", "encoded-password", "홍길동");
        when(memberRepository.findByEmailAndDeletedAtIsNull("active@example.com")).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> emailVerificationService.sendReactivationCode("active@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_DORMANT);
        verify(emailCooldownGuard, never()).acquire(anyString(), anyString(), eq(30L));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 배치 미실행으로 status는 ACTIVE지만 6개월 이상 미접속한 휴면 대상 회원이 재활성화 코드를 요청하면 DORMANT로 동기화되고 코드가 발송되는지 검증
    @Test
    void 배치_미실행_휴면_대상_회원의_재활성화_코드_요청은_DORMANT로_동기화되며_성공한다() {
        Member member = Member.signUp("eligible@example.com", "encoded-password", "홍길동");
        ReflectionTestUtils.setField(member, "lastLoginAt", LocalDateTime.now().minusMonths(7));
        when(memberRepository.findByEmailAndDeletedAtIsNull("eligible@example.com")).thenReturn(Optional.of(member));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        emailVerificationService.sendReactivationCode("eligible@example.com");

        assertThat(member.getStatus()).isEqualTo(MemberStatus.DORMANT);
        verify(eventPublisher).publishEvent(any(ReactivationCodeIssuedEvent.class));
    }

    // 저장된 코드와 일치하는 코드로 재활성화 코드를 검증하면 코드가 삭제되는지 검증
    @Test
    void 올바른_재활성화_코드로_검증하면_코드가_삭제된다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email:reactivation:dormant@example.com")).thenReturn("482913");

        emailVerificationService.verifyReactivationCode("dormant@example.com", "482913");

        verify(redisTemplate).delete("email:reactivation:dormant@example.com");
    }

    // 저장된 코드와 다른 코드로 재활성화 코드를 검증하면 예외가 발생하는지 검증
    @Test
    void 재활성화_코드가_일치하지_않으면_검증에_실패한다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("email:reactivation:dormant@example.com")).thenReturn("482913");

        assertThatThrownBy(() -> emailVerificationService.verifyReactivationCode("dormant@example.com", "000000"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        verify(redisTemplate, never()).delete(anyString());
    }

    // 플래그가 없는 상태로 가드 호출 시 예외가 발생하는지 검증
    @Test
    void 인증완료_플래그가_없으면_assertVerified에서_예외가_발생한다() {
        when(redisTemplate.delete("email:verified:test@example.com")).thenReturn(false);

        assertThatThrownBy(() -> emailVerificationService.assertVerified("test@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }
}
