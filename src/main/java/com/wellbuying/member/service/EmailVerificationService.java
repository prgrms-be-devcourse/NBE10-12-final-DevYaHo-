package com.wellbuying.member.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.mail.EmailCooldownGuard;
import com.wellbuying.member.mail.MailService;
import com.wellbuying.member.repository.MemberRepository;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    private static final String CODE_KEY_PREFIX = "email:verification:";
    private static final String VERIFIED_KEY_PREFIX = "email:verified:";
    private static final String COOLDOWN_PURPOSE = "verification";
    private static final long CODE_TTL_MINUTES = 5L;
    private static final long COOLDOWN_SECONDS = 30L;
    private static final long VERIFIED_TTL_MINUTES = 30L;

    private final MailService mailService;
    private final EmailCooldownGuard emailCooldownGuard;
    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;

    public EmailVerificationService(MailService mailService, EmailCooldownGuard emailCooldownGuard,
            StringRedisTemplate redisTemplate, MemberRepository memberRepository) {
        this.mailService = mailService;
        this.emailCooldownGuard = emailCooldownGuard;
        this.redisTemplate = redisTemplate;
        this.memberRepository = memberRepository;
    }

    // 이미 가입된 이메일이면 거부, 쿨다운 확인 후 6자리 코드 생성 → Redis 저장(5분 TTL) → 쿨다운 선점(30초) → 메일 발송
    public void sendVerificationCode(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        emailCooldownGuard.check(COOLDOWN_PURPOSE, email);

        String code = generateCode();
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, Duration.ofMinutes(CODE_TTL_MINUTES));
        emailCooldownGuard.mark(COOLDOWN_PURPOSE, email, COOLDOWN_SECONDS);
        mailService.sendHtmlEmail(email, "[Wellbuying] 이메일 인증 코드", buildVerificationContent(code));
    }

    // Redis에 저장된 코드와 대조, 불일치/만료 시 예외. 성공 시 코드 삭제 + email:verified 플래그 저장(30분 TTL)
    public void verifyCode(String email, String code) {
        String codeKey = CODE_KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null || !stored.equals(code)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }
        redisTemplate.delete(codeKey);
        redisTemplate.opsForValue().set(VERIFIED_KEY_PREFIX + email, "1", Duration.ofMinutes(VERIFIED_TTL_MINUTES));
    }

    // signUp 진입 시 호출 — email:verified 플래그가 없으면 EMAIL_NOT_VERIFIED 예외(403), 있으면 소비(삭제) 후 가입 진행 허용
    public void assertVerified(String email) {
        Boolean deleted = redisTemplate.delete(VERIFIED_KEY_PREFIX + email);
        if (!Boolean.TRUE.equals(deleted)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    private String generateCode() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
    }

    private String buildVerificationContent(String code) {
        return """
                <p style="margin:0 0 8px;color:#333333;font-size:18px;font-weight:600;">이메일 인증 코드</p>
                <p style="margin:0 0 32px;color:#888888;font-size:14px;line-height:1.6;">
                  아래 인증 코드를 입력창에 입력해 주세요.<br>코드는 <strong>%d분</strong> 후 만료됩니다.
                </p>
                <div style="background-color:#f0f5ff;border:2px dashed #4A90E2;border-radius:8px;padding:24px;text-align:center;margin-bottom:32px;">
                  <span style="font-size:36px;font-weight:700;color:#4A90E2;letter-spacing:8px;">%s</span>
                </div>
                <p style="margin:0;color:#aaaaaa;font-size:12px;line-height:1.6;">
                  본인이 요청하지 않은 경우 이 메일을 무시해 주세요.<br>
                  인증 코드를 타인에게 공유하지 마세요.
                </p>
                """.formatted(CODE_TTL_MINUTES, code);
    }
}
