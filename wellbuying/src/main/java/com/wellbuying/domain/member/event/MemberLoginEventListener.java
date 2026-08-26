package com.wellbuying.domain.member.event;

import com.wellbuying.domain.member.service.MemberService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 로그인 시점의 lastLoginAt 갱신/휴면 전환을 인증 트랜잭션과 분리해 비동기로 처리
// - AFTER_COMMIT: 인증 트랜잭션이 커밋된 이후에 실행되어 REQUIRES_NEW처럼 커넥션 2개를 동시에 점유하지 않는다
// - fallbackExecution=true: issueOAuthExchangeCode()처럼 트랜잭션 없이 이벤트가 발행되는 경로에서도 정상 동작하도록 함
@Component
public class MemberLoginEventListener {

    private final MemberService memberService;

    public MemberLoginEventListener(MemberService memberService) {
        this.memberService = memberService;
    }

    @Async("memberEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleLoginEvent(MemberLoginEvent event) {
        memberService.updateLoginActivity(event.memberId());
    }
}
