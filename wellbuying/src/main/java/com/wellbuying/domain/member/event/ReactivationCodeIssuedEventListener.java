package com.wellbuying.domain.member.event;

import com.wellbuying.domain.member.mail.MailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReactivationCodeIssuedEventListener {

    private final MailService mailService;

    public ReactivationCodeIssuedEventListener(MailService mailService) {
        this.mailService = mailService;
    }

    // MailService.sendHtmlEmail()이 이미 @Async이고 내부에서 예외를 처리하므로 여기서는 추가 @Async/try-catch 불필요
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReactivationCodeIssued(ReactivationCodeIssuedEvent event) {
        mailService.sendHtmlEmail(event.email(), "[Wellbuying] 휴면 계정 재활성화 인증 코드", event.content());
    }
}
