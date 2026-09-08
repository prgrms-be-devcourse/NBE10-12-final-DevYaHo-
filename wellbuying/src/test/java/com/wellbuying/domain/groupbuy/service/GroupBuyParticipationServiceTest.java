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
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartResponse;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import java.time.LocalDateTime;
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
    private GroupBuyPartRepository groupBuyPartRepository;

    @Mock
    private GroupBuyCounterRepository groupBuyCounterRepository;

    @Mock
    private BuyerAddressRepository buyerAddressRepository;

    @InjectMocks
    private GroupBuyParticipationService groupBuyParticipationService;

    // 배송지 소유권 검증(findById 후 memberId 비교)을 통과시키기 위한 더미 주소록 항목.
    // id는 리플렉션으로 채운다 - BuyerAddress.create()는 저장 전이라 id가 없기 때문
    private BuyerAddress buyerAddressOf(Long id, Long memberId) {
        BuyerAddress buyerAddress = BuyerAddress.create(memberId, "서울특별시 강남구 테헤란로 123", "4층", "06234");
        org.springframework.test.util.ReflectionTestUtils.setField(buyerAddress, "id", id);
        return buyerAddress;
    }

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
        when(buyerAddressRepository.findById(1L)).thenReturn(Optional.of(buyerAddressOf(1L, 100L)));

        GroupBuyPartResponse response = groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(50, 1L));

        assertThat(response.quantity()).isEqualTo(50);
        assertThat(response.appliedPrice()).isNull();
        assertThat(groupBuy.getCurrentQuantity()).isEqualTo(50);
    }

    // 참여로 인해 최대 수량에 도달하면(매진) 공동구매가 즉시 SUCCESS로 확정되는지 검증. 확정 참여자 전원의
    // 최종가 반영/성사 이벤트 발행은 이 메서드가 더 이상 하지 않는다 - GroupBuyFinalizationWorker가 별도
    // 스케줄 틱에서 뒤이어 처리하므로(트리거 요청이 참여자 수와 무관하게 항상 빠르게 끝나야 함),
    // 응답의 appliedPrice는 매진 트리거 여부와 상관없이 null로 내려간다 (그 부분은 GroupBuyCloseProcessorTest 참고)
    @Test
    void 최대_수량에_도달하면_즉시_SUCCESS로_확정되지만_최종가_반영은_이_메서드가_하지_않는다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 100);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        stubAtomicIncrease(1L, groupBuy);
        when(groupBuyCounterRepository.tryIncrease(1L, 100, 100)).thenReturn(100L);
        when(groupBuyPartRepository.save(any(GroupBuyPart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(buyerAddressRepository.findById(1L)).thenReturn(Optional.of(buyerAddressOf(1L, 100L)));

        GroupBuyPartResponse response = groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(100, 1L));

        assertThat(groupBuy.getStatus().name()).isEqualTo("SUCCESS");
        assertThat(groupBuy.getFinalizedAt()).isNull();
        assertThat(response.appliedPrice()).isNull();
        verify(groupBuyPartRepository, never()).applyFinalPriceToConfirmedParts(any(), any(Integer.class), any());
        verify(groupBuyPartRepository, never()).findByGroupBuyIdAndStatus(any(), any());
    }

    // Redis 원자적 카운터가 재고 초과로 -1을 반환하면 GROUP_BUY_SOLD_OUT 예외가 발생하고 DB에는 아무것도 저장되지 않는지 검증
    @Test
    void 재고를_초과하면_참여에_실패한다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 100);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyCounterRepository.tryIncrease(1L, 50, 100)).thenReturn(-1L);
        when(buyerAddressRepository.findById(1L)).thenReturn(Optional.of(buyerAddressOf(1L, 100L)));

        assertThatThrownBy(() -> groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(50, 1L)))
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
                new GroupBuyPartCreateRequest(10, 1L)))
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
