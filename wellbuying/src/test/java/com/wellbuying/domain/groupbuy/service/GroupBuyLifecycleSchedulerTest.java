package com.wellbuying.domain.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
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
    private GroupBuyCloseProcessor groupBuyCloseProcessor;

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

    // 마감 시각이 지났고 최소 수량을 달성한 공동구매는 GroupBuyCloseProcessor.closeSucceeded로 최종 단가 및 확정
    // 참여자 목록과 함께 위임되는지 검증 (이벤트 발행은 이제 closeSucceeded 내부 책임이라 GroupBuyCloseProcessorTest가 다룬다)
    @Test
    void 최소_수량을_달성했으면_closeSucceeded로_위임하고_최종_단가를_소급_적용한다() {
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
        GroupBuy closedGroupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        closedGroupBuy.start();
        closedGroupBuy.succeed();
        when(groupBuyCloseProcessor.closeSucceeded(1L, 12_000, List.of(earlyParticipant))).thenReturn(closedGroupBuy);

        scheduler.closeOngoingGroupBuys();

        // 마감 시점 누적 수량(150)이 100명 구간을 넘겼으므로, 15,000원에 참여했던 사람도 최종가 12,000원으로 소급 적용된다
        assertThat(earlyParticipant.getAppliedPrice()).isEqualTo(12_000);
        verify(groupBuyCloseProcessor).closeSucceeded(1L, 12_000, List.of(earlyParticipant));
        verify(groupBuyCloseProcessor, never()).closeFailed(any());
    }

    // 마감 시각이 지났지만 최소 수량 미달인 공동구매는 GroupBuyCloseProcessor.closeFailed로 위임되는지 검증
    // (성사되지 않았으므로 가격 구간 조회 자체가 발생하지 않는다)
    @Test
    void 최소_수량_미달이면_closeFailed로_위임한다() {
        GroupBuy groupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        groupBuy.start();
        groupBuy.increaseQuantity(50);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqual(eq(GroupBuyStatus.ONGOING), any(), any()))
                .thenReturn(List.of(groupBuy));
        GroupBuy closedGroupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        closedGroupBuy.start();
        closedGroupBuy.fail();
        when(groupBuyCloseProcessor.closeFailed(1L)).thenReturn(closedGroupBuy);

        scheduler.closeOngoingGroupBuys();

        verify(groupBuyCloseProcessor).closeFailed(1L);
        verify(groupBuyCloseProcessor, never()).closeSucceeded(any(), any(Integer.class), any());
        verify(groupBuyPartRepository, never()).findByGroupBuyIdInAndStatus(any(), any());
        verify(groupBuyPriceRepository, never()).findByGroupBuyIdIn(any());
    }

    // 여러 공동구매가 한 배치에서 동시에 마감돼도 확정 참여자 조회(findByGroupBuyIdInAndStatus)/가격 구간 조회(findByGroupBuyIdIn)는
    // 건마다 반복 호출되지 않고 배치 전체에 대해 정확히 한 번씩만 호출되는지 검증 (N+1 회귀 방지),
    // 실제 상태 확정(closeSucceeded/closeFailed)은 건별로 호출되고, 서로 다른 최종가가 그룹별로 뒤섞이지 않고 각자에게 정확히 적용되는지도 검증
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
        when(groupBuyCloseProcessor.closeSucceeded(any(), any(Integer.class), any())).thenReturn(succeeded1);
        when(groupBuyCloseProcessor.closeFailed(any())).thenReturn(failed);

        scheduler.closeOngoingGroupBuys();

        // 공동구매 1은 150명(12,000원 구간), 공동구매 2는 1,000명(10,000원 구간) - 서로 다른 최종가가 뒤섞이지 않는다
        assertThat(part1.getAppliedPrice()).isEqualTo(12_000);
        assertThat(part2.getAppliedPrice()).isEqualTo(10_000);
        // 대상이 3건이지만 확정 참여자 조회/가격 구간 조회는 건별 반복이 아니라 딱 1번만 호출된다
        verify(groupBuyPartRepository, times(1)).findByGroupBuyIdInAndStatus(any(), any());
        verify(groupBuyPartRepository, never()).findByGroupBuyIdAndStatus(any(), any());
        verify(groupBuyPriceRepository, times(1)).findByGroupBuyIdIn(any());
        // 실제 상태 확정은 건별로 별도 트랜잭션에 위임된다 (성사 2건 + 실패 1건)
        verify(groupBuyCloseProcessor).closeSucceeded(1L, 12_000, List.of(part1));
        verify(groupBuyCloseProcessor).closeSucceeded(2L, 10_000, List.of(part2));
        verify(groupBuyCloseProcessor).closeFailed(3L);
    }

    // 배치 중 한 건의 마감 처리에서 예외가 나도(예: closeFailed 도중 DB 오류) 나머지 건들은 영향받지 않고 정상적으로 마감되는지 검증
    // (배치 전체를 하나의 트랜잭션으로 묶었다면 한 건의 예외로 전체가 롤백됐을 상황)
    @Test
    void 한_건의_마감_처리가_실패해도_나머지_건은_영향받지_않는다() {
        GroupBuy failing = withId(1L, GroupBuy.create(10L, 1L, "실패할_건",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        failing.start();
        failing.increaseQuantity(10);
        GroupBuy healthy = withId(2L, GroupBuy.create(10L, 1L, "정상_건",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        healthy.start();
        healthy.increaseQuantity(5);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqual(eq(GroupBuyStatus.ONGOING), any(), any()))
                .thenReturn(List.of(failing, healthy));
        when(groupBuyCloseProcessor.closeFailed(1L)).thenThrow(new RuntimeException("DB 오류"));
        GroupBuy closedHealthy = withId(2L, GroupBuy.create(10L, 1L, "정상_건",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        closedHealthy.start();
        closedHealthy.fail();
        when(groupBuyCloseProcessor.closeFailed(2L)).thenReturn(closedHealthy);

        scheduler.closeOngoingGroupBuys();

        verify(groupBuyCloseProcessor).closeFailed(1L);
        verify(groupBuyCloseProcessor).closeFailed(2L);
    }
}
