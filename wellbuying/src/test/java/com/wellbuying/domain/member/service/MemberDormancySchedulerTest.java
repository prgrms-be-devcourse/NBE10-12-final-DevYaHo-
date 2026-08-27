package com.wellbuying.domain.member.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberDormancySchedulerTest {

    private static final int BATCH_SIZE = 500;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberDormancyScheduler scheduler;

    // 대상이 없으면 배치를 1회만 호출하고 종료하는지 검증
    @Test
    void 대상이_없으면_배치를_한번만_호출하고_종료한다() {
        when(memberService.markDormantBatch(any(), eq(BATCH_SIZE))).thenReturn(0);

        scheduler.markDormantMembers();

        verify(memberService, times(1)).markDormantBatch(any(), eq(BATCH_SIZE));
    }

    // 배치가 batchSize만큼 꽉 찬 경우, 더 이상 전환할 대상이 없어질 때까지(0건 반환) 반복 호출하는지 검증
    @Test
    void 전환된_건수가_0이_될때까지_배치를_반복_호출한다() {
        when(memberService.markDormantBatch(any(), eq(BATCH_SIZE)))
                .thenReturn(BATCH_SIZE, BATCH_SIZE, 3, 0);

        scheduler.markDormantMembers();

        verify(memberService, times(4)).markDormantBatch(any(), eq(BATCH_SIZE));
    }
}
