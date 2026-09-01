package com.wellbuying.domain.member.event;

import com.wellbuying.domain.member.mail.MailService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class VerificationMailEventListener {

    private final MailService mailService;

    public VerificationMailEventListener(MailService mailService) {
        this.mailService = mailService;
    }

    // sendReactivationCode()는 @Transactional이므로 커밋 이후에만 발송 - 롤백 시 이미 커밋된 Redis 쿨다운/코드 상태와의 불일치를 피함
    // MailService.sendHtmlEmail()이 이미 @Async이고 내부에서 예외를 처리하므로 여기서는 추가 @Async/try-catch 불필요
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReactivationCodeIssued(ReactivationCodeIssuedEvent event) {
        mailService.sendHtmlEmail(event.email(), "[Wellbuying] 휴면 계정 재활성화 인증 코드", event.content());
    }

    // sendVerificationCode()는 트랜잭션 없이 호출되므로(DB 상태 변경 없음) AFTER_COMMIT이 아닌 일반 EventListener로 처리
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleVerificationCodeIssued(VerificationCodeIssuedEvent event) {
        mailService.sendHtmlEmail(event.email(), "[Wellbuying] 이메일 인증 코드", event.content());
    }
}
