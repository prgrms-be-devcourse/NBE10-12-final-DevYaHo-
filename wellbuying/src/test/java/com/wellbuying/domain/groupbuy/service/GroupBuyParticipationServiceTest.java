package com.wellbuying.domain.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
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
import org.springframework.transaction.PlatformTransactionManager;

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

    // participate()가 TransactionTemplate으로 STEP1/STEP3 트랜잭션 경계를 직접 잡으므로 필요 - 이 mock은
    // getTransaction()/commit()/rollback() 전부 기본 no-op(null 반환)이라 별도 스텁 없이도 콜백이 그대로 실행된다
    @Mock
    private PlatformTransactionManager transactionManager;

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

    // groupBuyRepository.increaseQuantity()는 실제로는 DB에서 원자적으로(+ status/end_at 조건까지 같이)
    // 증가시키지만, mock은 아무 동작도 하지 않으므로 그 효과(엔티티의 currentQuantity 증가, 영향받은 행 수 1)를
    // 테스트에서 직접 재현해줘야 production 코드의 isSoldOut() 판정 등이 실제와 동일하게 동작한다. groupBuy는
    // 저장된 적 없어 id가 null이라 groupBuyId를 별도로 받아 매칭한다
    private void stubAtomicIncrease(Long groupBuyId, GroupBuy groupBuy) {
        doAnswer(invocation -> {
            int delta = invocation.getArgument(1);
            groupBuy.increaseQuantity(delta);
            return 1;
        }).when(groupBuyRepository).increaseQuantity(eq(groupBuyId), anyInt(), eq(GroupBuyStatus.ONGOING), any());
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
        verify(groupBuyRepository, never()).increaseQuantity(any(), anyInt(), any(), any());
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

    // STEP1과 STEP3 사이(트랜잭션이 나뉘어 있어 생기는 틈)에 공동구매가 마감되거나 판매정지되면, STEP3의
    // 조건부 UPDATE(status=ONGOING AND end_at>now)가 0건을 반영하고 GROUP_BUY_NOT_ONGOING으로 거부되며,
    // 이미 늘려둔 Redis 카운터도 되돌려지는지 검증 - status 변경/end_at 경과 두 원인 모두 이 경로(0건 반영)로 수렴한다
    @Test
    void STEP1_이후_상태가_바뀌면_STEP3에서_거부되고_Redis_카운터를_보상한다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyCounterRepository.tryIncrease(1L, 50, 10_000)).thenReturn(50L);
        when(buyerAddressRepository.findById(1L)).thenReturn(Optional.of(buyerAddressOf(1L, 100L)));
        when(groupBuyPartRepository.save(any(GroupBuyPart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // STEP3 시점엔 이미 상태가 바뀌어(마감/판매정지) 조건부 UPDATE가 0건 반영됐다고 가정
        when(groupBuyRepository.increaseQuantity(eq(1L), anyInt(), eq(GroupBuyStatus.ONGOING), any())).thenReturn(0);

        assertThatThrownBy(() -> groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(50, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_NOT_ONGOING);

        verify(groupBuyCounterRepository, times(1)).decrease(1L, 50);
        verify(groupBuyCounterRepository, never()).delete(any());
    }

    // STEP3 콜백 실행 중 예외(위 테스트처럼 검증 실패든, 그 외 런타임 예외든)가 나면 Redis 카운터 보상이
    // 정확히 1번만 호출되는지 검증 - 중복 보상으로 카운터가 더 깎이면 안 된다
    @Test
    void STEP3_콜백에서_예외가_나면_Redis_보상은_정확히_1번만_호출된다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyCounterRepository.tryIncrease(1L, 50, 10_000)).thenReturn(50L);
        when(buyerAddressRepository.findById(1L)).thenReturn(Optional.of(buyerAddressOf(1L, 100L)));
        when(groupBuyPartRepository.save(any(GroupBuyPart.class)))
                .thenThrow(new RuntimeException("DB 저장 실패(예: 제약 위반)"));

        assertThatThrownBy(() -> groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(50, 1L)))
                .isInstanceOf(RuntimeException.class);

        verify(groupBuyCounterRepository, times(1)).decrease(1L, 50);
    }

    // STEP3 콜백 자체는 정상 반환됐지만 그 뒤 commit 단계에서 실패하는 경우(락 경합, DB 커넥션 장애 등)를
    // 재현한다 - TransactionTemplate은 콜백 반환 이후 commit()을 호출하므로, commit()만 실패하도록 stub한다.
    // 이때 매진 확정으로 Redis 카운터를 지우는 delete()가 호출되지 않아야 한다(호출됐다면 그 이후 보상 decrease()가
    // 이미 삭제된 키에 음수 값을 만들어버리는 이번 리뷰의 핵심 시나리오가 재현된다)
    @Test
    void STEP3_commit이_실패하면_매진_삭제_없이_Redis_카운터를_보상한다() {
        GroupBuy groupBuy = ongoingGroupBuy(100, 100);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));
        stubAtomicIncrease(1L, groupBuy);
        when(groupBuyCounterRepository.tryIncrease(1L, 100, 100)).thenReturn(100L);
        when(groupBuyPartRepository.save(any(GroupBuyPart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(buyerAddressRepository.findById(1L)).thenReturn(Optional.of(buyerAddressOf(1L, 100L)));
        // STEP1의 commit은 성공시키고(doNothing), STEP3의 commit에서만 실패시킨다(두 번째 호출)
        doNothing().doThrow(new RuntimeException("commit 실패")).when(transactionManager).commit(any());

        assertThatThrownBy(() -> groupBuyParticipationService.participate(100L, 1L,
                new GroupBuyPartCreateRequest(100, 1L)))
                .isInstanceOf(RuntimeException.class);

        verify(groupBuyCounterRepository, times(1)).decrease(1L, 100);
        verify(groupBuyCounterRepository, never()).delete(any());
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
