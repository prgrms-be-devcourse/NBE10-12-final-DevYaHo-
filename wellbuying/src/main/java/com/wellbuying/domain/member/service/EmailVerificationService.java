package com.wellbuying.domain.member.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.event.ReactivationCodeIssuedEvent;
import com.wellbuying.domain.member.event.VerificationCodeIssuedEvent;
import com.wellbuying.domain.member.mail.EmailCooldownGuard;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final String CODE_KEY_PREFIX = "email:verification:";
    private static final String VERIFIED_KEY_PREFIX = "email:verified:";
    private static final String COOLDOWN_PURPOSE = "verification";
    private static final String REACTIVATION_CODE_KEY_PREFIX = "email:reactivation:";
    private static final String REACTIVATION_COOLDOWN_PURPOSE = "reactivation";
    private static final long CODE_TTL_MINUTES = 5L;
    private static final long COOLDOWN_SECONDS = 30L;
    private static final long VERIFIED_TTL_MINUTES = 30L;

    private final EmailCooldownGuard emailCooldownGuard;
    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EmailVerificationService(EmailCooldownGuard emailCooldownGuard,
            StringRedisTemplate redisTemplate, MemberRepository memberRepository,
            ApplicationEventPublisher eventPublisher) {
        this.emailCooldownGuard = emailCooldownGuard;
        this.redisTemplate = redisTemplate;
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
    }

    // 이미 가입된 이메일이면 거부, 쿨다운 선점(SETNX, 30초) 후 6자리 코드 생성 → Redis 저장(5분 TTL) → 메일 발송 이벤트 발행
    public void sendVerificationCode(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        emailCooldownGuard.acquire(COOLDOWN_PURPOSE, email, COOLDOWN_SECONDS);

        String code = generateCode();
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, code, Duration.ofMinutes(CODE_TTL_MINUTES));
        eventPublisher.publishEvent(new VerificationCodeIssuedEvent(email, buildVerificationContent(code)));
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

    // 휴면 회원만 재활성화 코드를 받을 수 있음 - 존재하지 않으면 거부, 배치 미실행으로 아직 ACTIVE인 휴면 대상은 이 시점에 DORMANT로 동기화하여 허용
    // validateCanReactivate()를 acquire()보다 앞에 두어 재활성화 대상이 아닌 요청에 쿨다운을 낭비하지 않도록 함.
    // 메일 발송은 AFTER_COMMIT 이벤트로 분리 - SMTP 실패로 트랜잭션이 롤백돼도 이미 커밋된 Redis 상태와의 불일치가 생기지 않음
    @Transactional
    public void sendReactivationCode(String email) {
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.validateCanReactivate();
        emailCooldownGuard.acquire(REACTIVATION_COOLDOWN_PURPOSE, email, COOLDOWN_SECONDS);

        String code = generateCode();
        redisTemplate.opsForValue()
                .set(REACTIVATION_CODE_KEY_PREFIX + email, code, Duration.ofMinutes(CODE_TTL_MINUTES));
        eventPublisher.publishEvent(new ReactivationCodeIssuedEvent(email, buildVerificationContent(code)));
    }

    // 재활성화 코드 검증 - 가입 흐름과 달리 검증과 재활성화가 한 호출(AuthService.reactivate())에서 처리되므로 별도 verified 플래그 없이 코드만 소비
    public void verifyReactivationCode(String email, String code) {
        String codeKey = REACTIVATION_CODE_KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null || !stored.equals(code)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }
        redisTemplate.delete(codeKey);
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
