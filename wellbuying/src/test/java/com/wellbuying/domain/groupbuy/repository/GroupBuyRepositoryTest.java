package com.wellbuying.domain.groupbuy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class GroupBuyRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @Autowired
    private MemberRepository memberRepository;

    private GroupBuy save(GroupBuyStatus status, LocalDateTime startAt, LocalDateTime endAt) {
        Member producer = memberRepository.save(
                Member.signUp("producer-" + System.nanoTime() + "@example.com", "encoded-password", "생산자"));
        GroupBuy groupBuy = GroupBuy.create(10L, producer.getId(), "제목", startAt, endAt, 100, 1_000);
        GroupBuy saved = groupBuyRepository.save(groupBuy);
        if (status == GroupBuyStatus.ONGOING) {
            saved.start();
        } else if (status == GroupBuyStatus.CANCELED) {
            saved.cancel();
        }
        return groupBuyRepository.save(saved);
    }

    // findByStatus가 해당 상태의 공동구매만 페이지로 반환하는지 검증
    @Test
    void findByStatus는_해당_상태만_조회한다() {
        save(GroupBuyStatus.READY, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8));
        save(GroupBuyStatus.ONGOING, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        var page = groupBuyRepository.findByStatus(GroupBuyStatus.ONGOING, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(GroupBuyStatus.ONGOING);
    }

    // findByStatusAndStartAtLessThanEqual이 시작 시각이 지난 READY 공동구매만 반환하는지 검증
    @Test
    void findByStatusAndStartAtLessThanEqual은_시작_시각이_지난_READY만_조회한다() {
        save(GroupBuyStatus.READY, LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(8));
        save(GroupBuyStatus.READY, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8));

        var results = groupBuyRepository.findByStatusAndStartAtLessThanEqual(GroupBuyStatus.READY,
                LocalDateTime.now(), Limit.of(10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStartAt()).isBefore(LocalDateTime.now());
    }

    // findByStatusAndEndAtLessThanEqual이 마감 시각이 지난 ONGOING 공동구매만 반환하는지 검증
    @Test
    void findByStatusAndEndAtLessThanEqual은_마감_시각이_지난_ONGOING만_조회한다() {
        save(GroupBuyStatus.ONGOING, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1));
        save(GroupBuyStatus.ONGOING, LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(1));

        var results = groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(GroupBuyStatus.ONGOING,
                LocalDateTime.now(), Limit.of(10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEndAt()).isBefore(LocalDateTime.now());
    }

    // 밀린 건이 BATCH_LIMIT을 넘어 한 번에 다 처리되지 못하더라도, 가장 먼저 마감된(오래 기다린) 건부터
    // 처리되도록 end_at 오름차순으로 반환하는지 검증
    @Test
    void findByStatusAndEndAtLessThanEqualOrderByEndAtAsc은_먼저_마감된_건부터_반환한다() {
        GroupBuy later = save(GroupBuyStatus.ONGOING, LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusMinutes(1));
        GroupBuy earliest = save(GroupBuyStatus.ONGOING, LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusDays(1));

        var results = groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(GroupBuyStatus.ONGOING,
                LocalDateTime.now(), Limit.of(10));

        assertThat(results).extracting(GroupBuy::getId).containsExactly(earliest.getId(), later.getId());
    }

    // limit이 실제로 결과 건수를 제한하는지 검증 (스케줄러가 한 번에 처리할 최대 건수를 넘지 않도록 보장하는 부분)
    @Test
    void limit을_넘는_대상은_한_번에_조회되지_않는다() {
        for (int i = 0; i < 3; i++) {
            save(GroupBuyStatus.ONGOING, LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1));
        }

        var results = groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(GroupBuyStatus.ONGOING,
                LocalDateTime.now(), Limit.of(2));

        assertThat(results).hasSize(2);
    }
}
