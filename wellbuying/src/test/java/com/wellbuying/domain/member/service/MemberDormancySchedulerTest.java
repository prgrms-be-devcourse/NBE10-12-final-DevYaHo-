package com.wellbuying.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberDormancySchedulerTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberDormancyScheduler scheduler;

    // 배치 대상으로 조회된 회원들이 모두 DORMANT로 전환되는지 검증
    @Test
    void 배치_대상_회원을_모두_DORMANT로_전환한다() {
        Member member1 = Member.signUp("dormant1@example.com", "encoded-password", "홍길동");
        Member member2 = Member.signUp("dormant2@example.com", "encoded-password", "김철수");
        when(memberRepository.findByStatusAndLastLoginAtBefore(eq(MemberStatus.ACTIVE), any(), any()))
                .thenReturn(List.of(member1, member2));

        scheduler.markDormantMembers();

        assertThat(member1.getStatus()).isEqualTo(MemberStatus.DORMANT);
        assertThat(member2.getStatus()).isEqualTo(MemberStatus.DORMANT);
    }
}
