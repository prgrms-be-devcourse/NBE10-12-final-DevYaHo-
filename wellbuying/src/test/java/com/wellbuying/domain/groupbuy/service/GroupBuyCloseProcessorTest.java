package com.wellbuying.domain.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// closeSucceeded/closeFailed는 GroupBuyLifecycleSchedulerTest가 아니라 여기서 검증한다 -
// 상태 확정 + 참여자 최종가 반영 + 이벤트(아웃박스) 기록이 정확히 같은 메서드(=같은 트랜잭션) 안에서
// 함께 호출되는지가 이 클래스의 핵심 책임이라, 스케줄러 쪽 목(mock)만으로는 그 원자성을 검증할 수 없다
@ExtendWith(MockitoExtension.class)
class GroupBuyCloseProcessorTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;

    @Mock
    private GroupBuyCounterRepository groupBuyCounterRepository;

    @Mock
    private GroupBuyEventPublisher groupBuyEventPublisher;

    @InjectMocks
    private GroupBuyCloseProcessor closeProcessor;

    private GroupBuy ongoingGroupBuy() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusMinutes(1), 100, 1_000);
        groupBuy.start();
        return groupBuy;
    }

    // 최소 수량 달성 시 SUCCESS로 확정하고, 확정 참여자 전원에게 최종 단가를 벌크 UPDATE로 반영하고,
    // Redis 카운터를 지우고, 성사 이벤트를 (호출자가 넘겨준 확정 참여자 목록 그대로) 기록하는지 검증
    @Test
    void closeSucceeded는_상태_확정과_최종가_반영과_이벤트_기록을_모두_수행한다() {
        GroupBuy groupBuy = ongoingGroupBuy();
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        GroupBuyPart confirmedPart = GroupBuyPart.confirm(1L, 100L, 150);

        GroupBuy result = closeProcessor.closeSucceeded(1L, 12_000, List.of(confirmedPart));

        assertThat(result.getStatus().name()).isEqualTo("SUCCESS");
        verify(groupBuyPartRepository).applyFinalPriceToConfirmedParts(1L, 12_000, GroupBuyPartStatus.CONFIRMED);
        verify(groupBuyCounterRepository).delete(1L);
        verify(groupBuyEventPublisher).publishCompleted(groupBuy, List.of(confirmedPart));
    }

    // 최소 수량 미달 시 FAILED로 확정하고, Redis 카운터를 지우고, 실패 이벤트를 기록하는지 검증
    // (성사가 아니므로 참여자 최종가 반영은 일어나지 않는다)
    @Test
    void closeFailed는_상태_확정과_이벤트_기록을_수행하고_최종가_반영은_하지_않는다() {
        GroupBuy groupBuy = ongoingGroupBuy();
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));

        GroupBuy result = closeProcessor.closeFailed(1L);

        assertThat(result.getStatus().name()).isEqualTo("FAILED");
        verify(groupBuyCounterRepository).delete(1L);
        verify(groupBuyEventPublisher).publishFailed(groupBuy);
        verify(groupBuyPartRepository, org.mockito.Mockito.never())
                .applyFinalPriceToConfirmedParts(any(), any(Integer.class), any());
    }

    // 대상 공동구매를 찾지 못하면 예외를 던지고, 상태 변경/카운터 삭제/이벤트 기록 중 아무것도 일어나지 않는지 검증
    @Test
    void 존재하지_않는_공동구매면_예외를_던지고_아무것도_수행하지_않는다() {
        when(groupBuyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> closeProcessor.closeFailed(999L)).isInstanceOf(BusinessException.class);

        verify(groupBuyCounterRepository, org.mockito.Mockito.never()).delete(any());
        verify(groupBuyEventPublisher, org.mockito.Mockito.never()).publishFailed(any());
    }
}
