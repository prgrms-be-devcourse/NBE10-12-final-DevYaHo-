package com.wellbuying.domain.member.service;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 매일 새벽 마지막 로그인이 DORMANT_THRESHOLD_MONTHS 이전인 ACTIVE 회원을 휴면 전환하는 배치.
// 로그인 시점의 lazy 체크(MemberService.updateLoginActivity)는 최근 접속자만 즉시 반영하므로,
// 접속하지 않는 회원까지 포함해 상태를 정리하기 위해 별도 스케줄러로 보완한다
@Component
public class MemberDormancyScheduler {

    private static final Limit BATCH_LIMIT = Limit.of(500);

    private final MemberRepository memberRepository;

    public MemberDormancyScheduler(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void markDormantMembers() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(Member.DORMANT_THRESHOLD_MONTHS);
        memberRepository.findByStatusAndLastLoginAtBefore(MemberStatus.ACTIVE, threshold, BATCH_LIMIT)
                .forEach(Member::markDormant);
    }
}
