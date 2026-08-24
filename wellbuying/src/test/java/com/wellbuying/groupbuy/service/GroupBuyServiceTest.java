package com.wellbuying.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.service.GroupBuyService;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.domain.GroupBuy;
import com.wellbuying.domain.groupbuy.domain.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest.PriceTierRequest;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupBuyServiceTest {

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

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private GroupBuyService groupBuyService;

    private GroupBuyCreateRequest createRequest() {
        return new GroupBuyCreateRequest(10L, "정직한 사과 공동구매",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8),
                100, 10_000,
                List.of(new PriceTierRequest(1, 100, 15_000), new PriceTierRequest(2, 1_000, 12_000)));
    }

    private Member sellerMember() {
        Member member = mock(Member.class);
        when(member.getRole()).thenReturn(Role.SELLER);
        return member;
    }

    // SELLER 역할의 회원이 유효한 요청으로 공동구매를 생성하면 가격 구간 저장과 Redis 카운터 초기화까지 함께 처리되는지 검증
    @Test
    void 셀러가_공동구매_생성에_성공한다() {
        Member seller = sellerMember();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        when(groupBuyRepository.save(any(GroupBuy.class))).thenAnswer(invocation -> invocation.getArgument(0));

        groupBuyService.create(1L, createRequest());

        verify(groupBuyRepository).save(any(GroupBuy.class));
        verify(groupBuyPriceRepository).saveAll(any());
        verify(groupBuyCounterRepository).initialize(any(), any());
    }

    // BUYER 역할의 회원이 공동구매 생성을 시도하면 GROUP_BUY_FORBIDDEN 예외가 발생하는지 검증
    @Test
    void 셀러가_아니면_공동구매_생성에_실패한다() {
        Member buyer = mock(Member.class);
        when(buyer.getRole()).thenReturn(Role.BUYER);
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(buyer));

        assertThatThrownBy(() -> groupBuyService.create(1L, createRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_FORBIDDEN);
        verify(groupBuyRepository, never()).save(any());
    }

    // 시작일이 마감일보다 이후이면 GROUP_BUY_INVALID_PERIOD 예외가 발생하는지 검증
    @Test
    void 시작일이_마감일보다_늦으면_생성에_실패한다() {
        Member seller = sellerMember();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        GroupBuyCreateRequest invalidRequest = new GroupBuyCreateRequest(10L, "제목",
                LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(1),
                100, 10_000, List.of(new PriceTierRequest(1, 100, 15_000)));

        assertThatThrownBy(() -> groupBuyService.create(1L, invalidRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_INVALID_PERIOD);
        verify(groupBuyRepository, never()).save(any());
    }

    // 최소 수량이 최대 수량보다 크면 GROUP_BUY_INVALID_QUANTITY 예외가 발생하는지 검증
    @Test
    void 최소_수량이_최대_수량보다_크면_생성에_실패한다() {
        Member seller = sellerMember();
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        GroupBuyCreateRequest invalidRequest = new GroupBuyCreateRequest(10L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8),
                10_000, 100, List.of(new PriceTierRequest(1, 100, 15_000)));

        assertThatThrownBy(() -> groupBuyService.create(1L, invalidRequest))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_INVALID_QUANTITY);
        verify(groupBuyRepository, never()).save(any());
    }

    // 생산자 본인이 시작 전(READY) 공동구매를 취소하면 상태가 CANCELED로 바뀌고 Redis 카운터가 정리되며 이벤트가 발행되는지 검증
    @Test
    void 생산자가_시작_전_공동구매_취소에_성공한다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));

        groupBuyService.cancel(1L, 1L);

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.CANCELED);
        verify(groupBuyCounterRepository).delete(1L);
        verify(groupBuyEventPublisher, times(1)).publishCanceled(groupBuy);
    }

    // 생산자 본인이 아닌 회원이 취소를 시도하면 GROUP_BUY_FORBIDDEN 예외가 발생하는지 검증
    @Test
    void 생산자가_아니면_취소에_실패한다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));

        assertThatThrownBy(() -> groupBuyService.cancel(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_FORBIDDEN);
        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.READY);
    }

    // 이미 시작된(ONGOING) 공동구매는 취소할 수 없어 GROUP_BUY_CANCEL_NOT_ALLOWED 예외가 발생하는지 검증
    @Test
    void 이미_시작된_공동구매는_취소에_실패한다() {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(8), 100, 10_000);
        groupBuy.start();
        when(groupBuyRepository.findById(1L)).thenReturn(Optional.of(groupBuy));

        assertThatThrownBy(() -> groupBuyService.cancel(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_BUY_CANCEL_NOT_ALLOWED);
        verify(groupBuyCounterRepository, never()).delete(anyLong());
    }
}
