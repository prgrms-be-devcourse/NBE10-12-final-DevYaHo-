package com.wellbuying.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.groupbuy.domain.GroupBuy;
import com.wellbuying.groupbuy.domain.GroupBuyPart;
import com.wellbuying.groupbuy.domain.GroupBuyPartStatus;
import com.wellbuying.groupbuy.domain.GroupBuyPrice;
import com.wellbuying.groupbuy.domain.GroupBuyStatus;
import com.wellbuying.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupBuyLifecycleSchedulerTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;

    @Mock
    private GroupBuyPriceRepository groupBuyPriceRepository;

    @Mock
    private GroupBuyCounterRepository groupBuyCounterRepository;

    @Mock
    private GroupBuyEventPublisher groupBuyEventPublisher;

    @InjectMocks
    private GroupBuyLifecycleScheduler scheduler;

    // 순수 자바 객체로 생성한 GroupBuy는 id가 없어, 배치 쿼리 키로 쓸 수 있도록 테스트에서만 id를 직접 세팅한다
    private GroupBuy withId(Long id, GroupBuy groupBuy) {
        ReflectionTestUtils.setField(groupBuy, "id", id);
        return groupBuy;
    }

    // 시작 시각이 지난 READY 공동구매를 ONGOING으로 전환하는지 검증
    @Test
    void 시작_시각이_지난_공동구매를_ONGOING으로_전환한다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusDays(1), 100, 1_000);
        when(groupBuyRepository.findByStatusAndStartAtLessThanEqual(eq(GroupBuyStatus.READY), any(), any()))
                .thenReturn(List.of(groupBuy));

        scheduler.openReadyGroupBuys();

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.ONGOING);
    }

    // 마감 시각이 지났고 최소 수량을 달성한 공동구매는 SUCCESS로 확정되고,
    // 마감 시점의 최종 누적 수량 기준 단가가 확정 참여자에게 소급 적용된 뒤 참여자별 성사 이벤트가 발행되는지 검증
    @Test
    void 최소_수량을_달성했으면_SUCCESS로_확정하고_최종_단가를_소급_적용한다() {
        GroupBuy groupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        groupBuy.start();
        groupBuy.increaseQuantity(150);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqual(eq(GroupBuyStatus.ONGOING), any(), any()))
                .thenReturn(List.of(groupBuy));
        // 참여 시점엔 가격을 저장하지 않으므로 appliedPrice는 아직 null인 상태
        GroupBuyPart earlyParticipant = GroupBuyPart.confirm(1L, 100L, 1);
        when(groupBuyPartRepository.findByGroupBuyIdInAndStatus(List.of(1L), GroupBuyPartStatus.CONFIRMED))
                .thenReturn(List.of(earlyParticipant));
        when(groupBuyPriceRepository.findByGroupBuyIdIn(List.of(1L)))
                .thenReturn(List.of(
                        GroupBuyPrice.of(1L, 1, 1, 15_000),
                        GroupBuyPrice.of(1L, 2, 100, 12_000)));

        scheduler.closeOngoingGroupBuys();

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.SUCCESS);
        // 마감 시점 누적 수량(150)이 100명 구간을 넘겼으므로, 15,000원에 참여했던 사람도 최종가 12,000원으로 소급 적용된다
        assertThat(earlyParticipant.getAppliedPrice()).isEqualTo(12_000);
        verify(groupBuyEventPublisher).publishCompleted(groupBuy, List.of(earlyParticipant));
        verify(groupBuyEventPublisher, never()).publishFailed(any());
        verify(groupBuyCounterRepository).deleteAll(List.of(1L));
    }

    // 마감 시각이 지났지만 최소 수량 미달인 공동구매는 FAILED로 확정되고, 실패 이벤트가 한 번만 발행되는지 검증
    // (성사되지 않았으므로 가격 구간 조회 자체가 발생하지 않는다)
    @Test
    void 최소_수량_미달이면_FAILED로_확정하고_실패_이벤트를_발행한다() {
        GroupBuy groupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        groupBuy.start();
        groupBuy.increaseQuantity(50);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqual(eq(GroupBuyStatus.ONGOING), any(), any()))
                .thenReturn(List.of(groupBuy));

        scheduler.closeOngoingGroupBuys();

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.FAILED);
        verify(groupBuyEventPublisher).publishFailed(groupBuy);
        verify(groupBuyEventPublisher, never()).publishCompleted(any(), any());
        verify(groupBuyPartRepository, never()).findByGroupBuyIdInAndStatus(any(), any());
        verify(groupBuyPriceRepository, never()).findByGroupBuyIdIn(any());
        verify(groupBuyCounterRepository).deleteAll(List.of(1L));
    }

    // 여러 공동구매가 한 배치에서 동시에 마감돼도 확정 참여자 조회(findByGroupBuyIdInAndStatus)/가격 구간 조회(findByGroupBuyIdIn)/
    // 카운터 정리(deleteAll)는 건마다 반복 호출되지 않고 배치 전체에 대해 정확히 한 번씩만 호출되는지 검증 (N+1 회귀 방지),
    // 그리고 서로 다른 최종가가 그룹별로 뒤섞이지 않고 각자에게 정확히 적용되는지도 함께 검증
    @Test
    void 여러_건이_동시에_마감돼도_최종_단가는_그룹별로_정확히_적용되고_조회는_한_번씩만_호출된다() {
        GroupBuy succeeded1 = withId(1L, GroupBuy.create(10L, 1L, "제목1",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        succeeded1.start();
        succeeded1.increaseQuantity(150);
        GroupBuy succeeded2 = withId(2L, GroupBuy.create(10L, 1L, "제목2",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        succeeded2.start();
        succeeded2.increaseQuantity(1_000);
        GroupBuy failed = withId(3L, GroupBuy.create(10L, 1L, "제목3",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        failed.start();
        failed.increaseQuantity(10);

        GroupBuyPart part1 = GroupBuyPart.confirm(1L, 100L, 150);
        GroupBuyPart part2 = GroupBuyPart.confirm(2L, 200L, 1_000);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqual(eq(GroupBuyStatus.ONGOING), any(), any()))
                .thenReturn(List.of(succeeded1, succeeded2, failed));
        when(groupBuyPartRepository.findByGroupBuyIdInAndStatus(List.of(1L, 2L), GroupBuyPartStatus.CONFIRMED))
                .thenReturn(List.of(part1, part2));
        when(groupBuyPriceRepository.findByGroupBuyIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(
                        GroupBuyPrice.of(1L, 1, 1, 15_000),
                        GroupBuyPrice.of(1L, 2, 100, 12_000),
                        GroupBuyPrice.of(2L, 1, 1, 15_000),
                        GroupBuyPrice.of(2L, 2, 1_000, 10_000)));

        scheduler.closeOngoingGroupBuys();

        assertThat(succeeded1.getStatus()).isEqualTo(GroupBuyStatus.SUCCESS);
        assertThat(succeeded2.getStatus()).isEqualTo(GroupBuyStatus.SUCCESS);
        assertThat(failed.getStatus()).isEqualTo(GroupBuyStatus.FAILED);
        // 공동구매 1은 150명(12,000원 구간), 공동구매 2는 1,000명(10,000원 구간) - 서로 다른 최종가가 뒤섞이지 않는다
        assertThat(part1.getAppliedPrice()).isEqualTo(12_000);
        assertThat(part2.getAppliedPrice()).isEqualTo(10_000);
        // 대상이 3건이지만 확정 참여자 조회/가격 구간 조회는 건별 반복이 아니라 딱 1번만 호출된다
        verify(groupBuyPartRepository, times(1)).findByGroupBuyIdInAndStatus(any(), any());
        verify(groupBuyPartRepository, never()).findByGroupBuyIdAndStatus(any(), any());
        verify(groupBuyPriceRepository, times(1)).findByGroupBuyIdIn(any());
        // Redis 카운터 정리도 건별 delete가 아니라 배치 전체에 대해 딱 1번의 deleteAll로 처리된다
        verify(groupBuyCounterRepository, times(1)).deleteAll(List.of(1L, 2L, 3L));
        verify(groupBuyCounterRepository, never()).delete(any());
    }
}
