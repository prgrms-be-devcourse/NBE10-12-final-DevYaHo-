package com.wellbuying.domain.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GroupBuyLifecycleSchedulerTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyCloseProcessor groupBuyCloseProcessor;

    private GroupBuyLifecycleScheduler scheduler;

    // 건별 마감 처리를 병렬 실행하는 실제 executor 대신, 테스트에서는 호출 스레드에서 즉시 동기 실행해
    // verify() 시점에 모든 처리가 이미 끝나있음을 보장한다 (Runnable::run은 execute()를 그 자리에서 바로 호출)
    @BeforeEach
    void setUp() {
        scheduler = new GroupBuyLifecycleScheduler(groupBuyRepository, groupBuyCloseProcessor, Runnable::run);
    }

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

    // 마감 시각이 지났고 최소 수량을 달성한 공동구매는 GroupBuyCloseProcessor.closeSucceeded로 위임되는지 검증
    // (최종 단가 계산·확정 참여자 조회·최종가 반영·이벤트 발행은 이제 GroupBuyFinalizationWorker의 책임이라
    // 스케줄러는 더 이상 가격 구간을 조회하지 않는다 - GroupBuyFinalizationWorkerTest가 그 부분을 다룬다)
    @Test
    void 최소_수량을_달성했으면_closeSucceeded로_위임한다() {
        GroupBuy groupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        groupBuy.start();
        groupBuy.increaseQuantity(150);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(eq(GroupBuyStatus.ONGOING), any(),
                any())).thenReturn(List.of(groupBuy));
        GroupBuy closedGroupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        closedGroupBuy.start();
        closedGroupBuy.succeed();
        when(groupBuyCloseProcessor.closeSucceeded(1L)).thenReturn(closedGroupBuy);

        scheduler.closeOngoingGroupBuys();

        verify(groupBuyCloseProcessor).closeSucceeded(1L);
        verify(groupBuyCloseProcessor, never()).closeFailed(any());
    }

    // 마감 시각이 지났지만 최소 수량 미달인 공동구매는 GroupBuyCloseProcessor.closeFailed로 위임되는지 검증
    @Test
    void 최소_수량_미달이면_closeFailed로_위임한다() {
        GroupBuy groupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        groupBuy.start();
        groupBuy.increaseQuantity(50);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(eq(GroupBuyStatus.ONGOING), any(),
                any())).thenReturn(List.of(groupBuy));
        GroupBuy closedGroupBuy = withId(1L, GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        closedGroupBuy.start();
        closedGroupBuy.fail();
        when(groupBuyCloseProcessor.closeFailed(1L)).thenReturn(closedGroupBuy);

        scheduler.closeOngoingGroupBuys();

        verify(groupBuyCloseProcessor).closeFailed(1L);
        verify(groupBuyCloseProcessor, never()).closeSucceeded(any());
    }

    // 여러 공동구매가 한 배치에서 동시에 마감돼도 각 건이 성사/미달 여부에 따라 정확히 closeSucceeded/closeFailed로
    // 나뉘어 위임되는지 검증. 최종 단가 계산은 이제 스케줄러의 책임이 아니므로(GroupBuyFinalizationWorker로 이동)
    // 여기서는 다루지 않는다
    @Test
    void 여러_건이_동시에_마감돼도_성사_미달_여부에_따라_각각_위임된다() {
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

        when(groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(eq(GroupBuyStatus.ONGOING), any(),
                any())).thenReturn(List.of(succeeded1, succeeded2, failed));
        when(groupBuyCloseProcessor.closeSucceeded(any())).thenReturn(succeeded1);
        when(groupBuyCloseProcessor.closeFailed(any())).thenReturn(failed);

        scheduler.closeOngoingGroupBuys();

        verify(groupBuyCloseProcessor).closeSucceeded(1L);
        verify(groupBuyCloseProcessor).closeSucceeded(2L);
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
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(eq(GroupBuyStatus.ONGOING), any(),
                any())).thenReturn(List.of(failing, healthy));
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

    // 한 라운드가 BATCH_LIMIT(500)만큼 꽉 차면 다음 60초 틱을 기다리지 않고 이번 틱 안에서 곧바로 다음 라운드를
    // 이어서 처리하는지 검증 - 마지막 라운드가 꽉 차지 않은 순간(더 이상 남지 않음) 멈춘다
    @Test
    void 라운드가_가득_차면_같은_틱_안에서_다음_라운드를_이어서_처리한다() {
        List<GroupBuy> fullRound = java.util.stream.IntStream.rangeClosed(1, 500)
                .mapToObj(i -> withId((long) i, GroupBuy.create(10L, 1L, "제목" + i,
                        LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000)))
                .peek(gb -> {
                    gb.start();
                    gb.increaseQuantity(150);
                })
                .toList();
        GroupBuy remaining = withId(501L, GroupBuy.create(10L, 1L, "제목501",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000));
        remaining.start();
        remaining.increaseQuantity(150);
        when(groupBuyRepository.findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(eq(GroupBuyStatus.ONGOING), any(),
                any())).thenReturn(fullRound, List.of(remaining));
        when(groupBuyCloseProcessor.closeSucceeded(any())).thenReturn(fullRound.get(0));

        scheduler.closeOngoingGroupBuys();

        verify(groupBuyRepository, org.mockito.Mockito.times(2))
                .findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(eq(GroupBuyStatus.ONGOING), any(), any());
        verify(groupBuyCloseProcessor).closeSucceeded(501L);
        for (long id = 1; id <= 500; id++) {
            verify(groupBuyCloseProcessor).closeSucceeded(id);
        }
    }
}
