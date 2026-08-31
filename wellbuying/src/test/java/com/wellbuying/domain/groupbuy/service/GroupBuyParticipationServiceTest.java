package com.wellbuying.domain.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartResponse;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupBuyParticipationServiceTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;

    @Mock
    private GroupBuyPriceRepository groupBuyPriceRepository;

    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;

    @Mock
    private GroupBuyCounterRepository groupBuyCounterRepository;

    @Mock
    private GroupBuyEventPublisher groupBuyEventPublisher;

    @InjectMocks
    private GroupBuyParticipationService groupBuyParticipationService;

    private GroupBuy ongoingGroupBuy(int minQuantity, int maxQuantity) {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), minQuantity, maxQuantity);
        groupBuy.start();
        return groupBuy;
    }

    // groupBuyRepository.increaseQuantity()는 실제로는 DB에서 원자적으로 증가시키지만, mock은 아무 동작도
    // 하지 않으므로 그 효과(엔티티의 currentQuantity 증가)를 테스트에서 직접 재현해줘야 production 코드의
    // isSoldOut() 판정 등이 실제와 동일하게 동작한다. groupBuy는 저장된 적 없어 id가 null이라 groupBuyId를
    // 별도로 받아 매칭한다
    private void stubAtomicIncrease(Long groupBuyId, GroupBuy groupBuy) {
        doAnswer(invocation -> {
            int delta = invocation.getArgument(1);
            groupBuy.increaseQuantity(delta);
            return null;
        }).when(groupBuyRepository).increaseQuantity(eq(groupBuyId), anyInt());
    }

    // 재고가 남아있는 상태에서 참여하면 참여 내역이 CONFIRMED로 저장되고, 아직 성사 전이라 가격은 null이며
    // (성사 여부와 무관하게 나중에 결정되므로 참여 시점엔 가격 구간 조회 자체를 하지 않는다), 매진 이벤트도 발행되지 않는지 검증
    @Test
    void 재고가_남아있으면_참여에_성공하고_가격은_아직_null이다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        stubAtomicIncrease(1L, groupBuy);
        when(groupBuyCounterRepository.tryIncrease(1L, 50, 10_000)).thenReturn(50L);
        when(groupBuyPartRepository.save(any(GroupBuyPart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupBuyPartResponse response = groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(50, "서울특별시 강남구 테헤란로 123", "4층", "06234"));

        assertThat(response.quantity()).isEqualTo(50);
        assertThat(response.appliedPrice()).isNull();
        assertThat(groupBuy.getCurrentQuantity()).isEqualTo(50);
        verify(groupBuyEventPublisher, never()).publishCompleted(any(), any());
        verify(groupBuyPriceRepository, never()).findByGroupBuyIdOrderByTierOrderAsc(any());
    }

    // 참여로 인해 최대 수량에 도달하면(매진) 공동구매가 즉시 SUCCESS로 확정되고 확정된 참여자 전체에 대해 성사 이벤트가 발행되는지 검증
    @Test
    void 최대_수량에_도달하면_즉시_성사되고_참여자별로_이벤트가_발행된다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 100);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        stubAtomicIncrease(1L, groupBuy);
        when(groupBuyCounterRepository.tryIncrease(1L, 100, 100)).thenReturn(100L);
        when(groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(1L))
                .thenReturn(List.of(GroupBuyPrice.of(1L, 1, 1, 15_000)));
        when(groupBuyPartRepository.save(any(GroupBuyPart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // 벌크 UPDATE는 mock이라 실제 DB 반영이 일어나지 않으므로, 재조회(findByGroupBuyIdAndStatus)가
        // 벌크 UPDATE 이후의 DB 상태(최종가가 이미 반영된 상태)를 반환한다고 가정하고 미리 값을 채워둔다
        GroupBuyPart confirmedPart = GroupBuyPart.confirm(1L, 100L, 100);
        confirmedPart.applyFinalPrice(15_000);
        when(groupBuyPartRepository.findByGroupBuyIdAndStatus(1L, GroupBuyPartStatus.CONFIRMED))
                .thenReturn(List.of(confirmedPart));

        groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(100, "서울특별시 강남구 테헤란로 123", "4층", "06234"));

        assertThat(groupBuy.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(confirmedPart.getAppliedPrice()).isEqualTo(15_000);
        // 확정 참여자 전원의 최종가는 개별 dirty checking이 아니라 벌크 UPDATE 한 문장으로 반영되는지 검증 (N+1 방지)
        verify(groupBuyPartRepository).applyFinalPriceToConfirmedParts(1L, 15_000, GroupBuyPartStatus.CONFIRMED);
        verify(groupBuyEventPublisher).publishCompleted(groupBuy, List.of(confirmedPart));
    }

    // 매진으로 성사되는 순간, 그보다 먼저 참여해 더 비싼 구간에 가격이 잠겨있던 참여자에게도 최종(가장 낮은) 구간 단가가
    // 소급 적용되는지 검증 - "먼저 참여한 사람도 나중에 가격이 내려가면 그 가격으로 통일돼야 한다"는 요구사항
    @Test
    void 매진으로_성사되면_먼저_참여한_사람의_가격도_최종가로_소급_적용된다() {
        GroupBuy groupBuy = ongoingGroupBuy(50, 100);
        // 이미 50명이 참여해 DB상 누적 수량이 50인 상태를 재현 (Redis의 newTotal=100과 일치시키기 위함)
        groupBuy.increaseQuantity(50);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        stubAtomicIncrease(1L, groupBuy);
        // 100명 도달 시점의 마지막 참여자 - 이 참여로 매진되며, 이 순간의 가격(10,000원)이 최종가다
        when(groupBuyCounterRepository.tryIncrease(1L, 50, 100)).thenReturn(100L);
        when(groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(1L))
                .thenReturn(List.of(
                        GroupBuyPrice.of(1L, 1, 1, 15_000),
                        GroupBuyPrice.of(1L, 2, 100, 10_000)));
        // save()로 실제 생성되는 참여 엔티티를 캡처해서, DB 조회(findByGroupBuyIdAndStatus)가 그 엔티티를 포함해
        // 반환하도록 재현한다 (실제 DB라면 방금 insert한 행도 같은 조회에 당연히 함께 잡힌다)
        GroupBuyPart[] savedPartHolder = new GroupBuyPart[1];
        when(groupBuyPartRepository.save(any(GroupBuyPart.class))).thenAnswer(invocation -> {
            savedPartHolder[0] = invocation.getArgument(0);
            return savedPartHolder[0];
        });
        // 참여 시점엔 가격을 저장하지 않으므로 아직 null이었다가, 매진 확정 시 벌크 UPDATE로 최종가가 반영된다.
        // 벌크 UPDATE는 mock이라 실제 DB 반영이 일어나지 않으므로, 재조회가 그 이후의 DB 상태를 반환한다고
        // 가정하고 미리 최종가(10,000원)를 채워둔다
        GroupBuyPart earlyParticipant = GroupBuyPart.confirm(1L, 200L, 50);
        earlyParticipant.applyFinalPrice(10_000);
        when(groupBuyPartRepository.findByGroupBuyIdAndStatus(1L, GroupBuyPartStatus.CONFIRMED))
                .thenAnswer(invocation -> List.of(earlyParticipant, savedPartHolder[0]));

        GroupBuyPartResponse response = groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(50, "서울특별시 강남구 테헤란로 123", "4층", "06234"));

        assertThat(response.appliedPrice()).isEqualTo(10_000);
        assertThat(earlyParticipant.getAppliedPrice()).isEqualTo(10_000);
        verify(groupBuyPartRepository).applyFinalPriceToConfirmedParts(1L, 10_000, GroupBuyPartStatus.CONFIRMED);
    }

    // Redis 원자적 카운터가 재고 초과로 -1을 반환하면 GROUP_BUY_SOLD_OUT 예외가 발생하고 DB에는 아무것도 저장되지 않는지 검증
    @Test
    void 재고를_초과하면_참여에_실패한다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 100);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyCounterRepository.tryIncrease(1L, 50, 100)).thenReturn(-1L);

        assertThatThrownBy(() -> groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(50, "서울특별시 강남구 테헤란로 123", "4층", "06234")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_SOLD_OUT);
        verify(groupBuyPartRepository, never()).save(any());
        verify(groupBuyRepository, never()).increaseQuantity(any(), anyInt());
    }

    // 아직 시작되지 않았거나(READY) 이미 끝난 공동구매에는 참여할 수 없어 GROUP_BUY_NOT_ONGOING 예외가 발생하는지 검증
    @Test
    void 진행중이_아닌_공동구매는_참여에_실패한다() {
        GroupBuy readyGroupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(readyGroupBuy));

        assertThatThrownBy(() -> groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(10, "서울특별시 강남구 테헤란로 123", "4층", "06234")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_NOT_ONGOING);
        verify(groupBuyCounterRepository, never()).tryIncrease(any(), anyInt(), anyInt());
    }

    // 참여자 본인이 진행 중인 공동구매의 참여를 취소하면 참여 상태가 CANCELED로 바뀌고 카운터가 원복되는지 검증
    @Test
    void 참여_취소에_성공한다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 10_000);
        GroupBuyPart part = GroupBuyPart.confirm(1L, 100L, 50);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyPartRepository.findByIdAndGroupBuyId(5L, 1L)).thenReturn(Optional.of(part));

        groupBuyParticipationService.cancelParticipation(100L, 1L, 5L);

        assertThat(part.getStatus()).isEqualTo(GroupBuyPartStatus.CANCELED);
        verify(groupBuyRepository).decreaseQuantity(1L, 50);
        verify(groupBuyCounterRepository).decrease(1L, 50);
    }

    // 참여자 본인이 아닌 회원이 취소를 시도하면 GROUP_BUY_PART_FORBIDDEN 예외가 발생하는지 검증
    @Test
    void 참여자_본인이_아니면_참여_취소에_실패한다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 10_000);
        GroupBuyPart part = GroupBuyPart.confirm(1L, 100L, 50);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyPartRepository.findByIdAndGroupBuyId(5L, 1L)).thenReturn(Optional.of(part));

        assertThatThrownBy(() -> groupBuyParticipationService.cancelParticipation(999L, 1L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_PART_FORBIDDEN);
        verify(groupBuyCounterRepository, never()).decrease(eq(1L), anyInt());
        verify(groupBuyRepository, never()).decreaseQuantity(any(), anyInt());
    }
}
