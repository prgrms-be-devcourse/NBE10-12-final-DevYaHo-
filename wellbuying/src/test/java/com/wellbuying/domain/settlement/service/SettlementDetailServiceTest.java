package com.wellbuying.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.settlement.dto.SettlementParticipantResponse;
import com.wellbuying.domain.settlement.dto.SettlementProgressResponse;
import com.wellbuying.domain.settlement.entity.SettlementItem;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementDetailServiceTest {

    @Mock
    private GroupBuyRepository groupBuyRepository;
    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;
    @Mock
    private SettlementItemRepository settlementItemRepository;
    @Mock
    private MemberRepository memberRepository;

    private SettlementDetailService service() {
        return new SettlementDetailService(groupBuyRepository, groupBuyPartRepository, settlementItemRepository,
                memberRepository);
    }

    private GroupBuy groupBuy(long producerId) {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getProducerId()).thenReturn(producerId);
        return groupBuy;
    }

    @Test
    void 진행도는_확정_참여자_수와_결제_완료_수를_반환한다() {
        GroupBuy groupBuy = groupBuy(5L);
        when(groupBuyRepository.findById(42L)).thenReturn(Optional.of(groupBuy));
        when(groupBuyPartRepository.countByGroupBuyIdAndStatus(42L, GroupBuyPartStatus.CONFIRMED)).thenReturn(8L);
        when(settlementItemRepository.countByGroupBuyId(42L)).thenReturn(5L);

        SettlementProgressResponse response = service().getProgress(5L, 42L);

        assertThat(response.totalParticipants()).isEqualTo(8L);
        assertThat(response.paidParticipants()).isEqualTo(5L);
    }

    @Test
    void 참여자_명단은_결제한_사람의_이름과_금액을_반환한다() {
        GroupBuy groupBuy = groupBuy(5L);
        when(groupBuyRepository.findById(42L)).thenReturn(Optional.of(groupBuy));
        SettlementItem item = SettlementItem.accrue(42L, 100L, 5L, 7L, 10_000, LocalDateTime.of(2026, 9, 1, 10, 0));
        when(settlementItemRepository.findByGroupBuyIdOrderByPaidAtAsc(42L)).thenReturn(List.of(item));
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(7L);
        when(member.getName()).thenReturn("이구매");
        when(memberRepository.findAllById(any())).thenReturn(List.of(member));

        List<SettlementParticipantResponse> participants = service().getParticipants(5L, 42L);

        assertThat(participants).hasSize(1);
        assertThat(participants.get(0).memberId()).isEqualTo(7L);
        assertThat(participants.get(0).memberName()).isEqualTo("이구매");
        assertThat(participants.get(0).amount()).isEqualTo(10_000);
    }

    @Test
    void 존재하지_않는_공동구매면_예외를_던진다() {
        when(groupBuyRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getProgress(5L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.GROUP_BUY_NOT_FOUND));
    }

    @Test
    void 다른_판매자의_공동구매면_예외를_던진다() {
        GroupBuy groupBuy = groupBuy(999L);
        when(groupBuyRepository.findById(42L)).thenReturn(Optional.of(groupBuy));

        assertThatThrownBy(() -> service().getProgress(5L, 42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.GROUP_BUY_FORBIDDEN));
    }
}
