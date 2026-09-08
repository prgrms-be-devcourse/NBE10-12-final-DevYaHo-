package com.wellbuying.domain.groupbuy.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
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
class GroupBuyFinalizationWorkerTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyPriceRepository groupBuyPriceRepository;

    @Mock
    private GroupBuyCloseProcessor groupBuyCloseProcessor;

    @InjectMocks
    private GroupBuyFinalizationWorker worker;

    private GroupBuy succeededWithId(Long id) {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000);
        groupBuy.start();
        groupBuy.succeed();
        ReflectionTestUtils.setField(groupBuy, "id", id);
        return groupBuy;
    }

    // 아직 최종화되지 않은 SUCCESS 건이 없으면 가격 구간 조회도, closeProcessor 위임도 일어나지 않는지 검증
    @Test
    void 대기_중인_건이_없으면_아무것도_하지_않는다() {
        when(groupBuyRepository.findByStatusAndFinalizedAtIsNullOrderByIdAsc(eq(GroupBuyStatus.SUCCESS), any()))
                .thenReturn(List.of());

        worker.finalizeSucceededGroupBuys();

        verify(groupBuyPriceRepository, never()).findByGroupBuyIdIn(any());
        verify(groupBuyCloseProcessor, never()).finalizeSucceeded(any(), any());
    }

    // 여러 건이 동시에 대기 중이어도 가격 구간 조회(findByGroupBuyIdIn)는 배치 전체에 대해 한 번만 호출되고(N+1 방지),
    // 실제 최종화(finalizeSucceeded)는 건별로 위임되며 서로 다른 공동구매의 가격 구간이 뒤섞이지 않는지 검증
    @Test
    void 여러_건이_대기_중이면_가격_구간_조회는_한_번만_하고_건별로_위임한다() {
        GroupBuy groupBuy1 = succeededWithId(1L);
        GroupBuy groupBuy2 = succeededWithId(2L);
        when(groupBuyRepository.findByStatusAndFinalizedAtIsNullOrderByIdAsc(eq(GroupBuyStatus.SUCCESS), any()))
                .thenReturn(List.of(groupBuy1, groupBuy2));
        GroupBuyPrice tier1 = GroupBuyPrice.of(1L, 1, 1, 15_000);
        GroupBuyPrice tier2 = GroupBuyPrice.of(2L, 1, 1, 9_000);
        when(groupBuyPriceRepository.findByGroupBuyIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(tier1, tier2));

        worker.finalizeSucceededGroupBuys();

        verify(groupBuyPriceRepository, org.mockito.Mockito.times(1)).findByGroupBuyIdIn(any());
        verify(groupBuyCloseProcessor).finalizeSucceeded(1L, List.of(tier1));
        verify(groupBuyCloseProcessor).finalizeSucceeded(2L, List.of(tier2));
    }

    // 한 건의 최종화 처리에서 예외가 나도(예: 가격 구간 계산 오류) 나머지 건은 영향받지 않고 정상적으로 처리되는지 검증
    // (GroupBuyLifecycleScheduler와 동일한 건별 예외 격리 패턴)
    @Test
    void 한_건의_최종화가_실패해도_나머지_건은_영향받지_않는다() {
        GroupBuy failing = succeededWithId(1L);
        GroupBuy healthy = succeededWithId(2L);
        when(groupBuyRepository.findByStatusAndFinalizedAtIsNullOrderByIdAsc(eq(GroupBuyStatus.SUCCESS), any()))
                .thenReturn(List.of(failing, healthy));
        when(groupBuyPriceRepository.findByGroupBuyIdIn(List.of(1L, 2L))).thenReturn(List.of());
        doThrow(new RuntimeException("가격 구간 없음"))
                .when(groupBuyCloseProcessor).finalizeSucceeded(eq(1L), any());

        worker.finalizeSucceededGroupBuys();

        verify(groupBuyCloseProcessor).finalizeSucceeded(1L, List.of());
        verify(groupBuyCloseProcessor).finalizeSucceeded(2L, List.of());
    }
}
