package com.wellbuying.domain.member.service;

import com.wellbuying.domain.member.entity.Member;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 매일 새벽 마지막 로그인이 DORMANT_THRESHOLD_MONTHS 이전인 ACTIVE 회원을 휴면 전환하는 배치.
// 로그인 시점의 lazy 체크(MemberService.updateLoginActivity)는 최근 접속자만 즉시 반영하므로,
// 접속하지 않는 회원까지 포함해 상태를 정리하기 위해 별도 스케줄러로 보완한다
@Component
public class MemberDormancyScheduler {

    private static final Logger log = LoggerFactory.getLogger(MemberDormancyScheduler.class);

    // 배치 1회에 처리할 건수 - 대상이 몇 명이든(1명이든 1만명이든) markDormantBatch를 반복 호출해 전부 소진하므로
    // 이 값은 배치 트랜잭션의 크기(락 보유 범위)를 제한하는 용도일 뿐, 하루 처리량 상한이 아니다
    private static final int BATCH_SIZE = 500;

    private final MemberService memberService;

    public MemberDormancyScheduler(MemberService memberService) {
        this.memberService = memberService;
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void markDormantMembers() {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(Member.DORMANT_THRESHOLD_MONTHS);
        int totalCount = 0;
        int batchCount;
        do {
            batchCount = memberService.markDormantBatch(threshold, BATCH_SIZE);
            totalCount += batchCount;
        } while (batchCount > 0);
        log.info("휴면 전환 배치 완료 - {}명 전환", totalCount);
    }
}
