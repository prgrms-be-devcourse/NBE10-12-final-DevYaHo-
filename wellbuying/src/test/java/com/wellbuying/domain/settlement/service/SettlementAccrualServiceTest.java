package com.wellbuying.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.settlement.entity.SettlementItem;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.event.PaymentCompletedMessage;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SettlementAccrualServiceTest {

    @Mock
    private SettlementItemRepository settlementItemRepository;

    private static final LocalDateTime PAID_AT = LocalDateTime.parse("2026-01-01T00:00:00");

    private PaymentCompletedMessage message() {
        return new PaymentCompletedMessage(42L, 50L, 100L, 5L, 10_000, PAID_AT);
    }

    @Test
    void 중복이_아니면_결제_원금을_담은_ACCRUED_상태의_정산_대상을_적립한다() {
        SettlementAccrualService service = new SettlementAccrualService(settlementItemRepository);
        when(settlementItemRepository.existsByGroupBuyParticipantId(50L)).thenReturn(false);
        when(settlementItemRepository.existsByGroupBuyIdAndStatus(42L, SettlementItemStatus.CONFIRMED))
                .thenReturn(false);

        service.accrue(message());

        ArgumentCaptor<SettlementItem> captor = ArgumentCaptor.forClass(SettlementItem.class);
        verify(settlementItemRepository, times(1)).save(captor.capture());
        SettlementItem saved = captor.getValue();
        assertThat(saved.getGroupBuyId()).isEqualTo(42L);
        assertThat(saved.getGroupBuyParticipantId()).isEqualTo(50L);
        assertThat(saved.getProducerId()).isEqualTo(5L);
        assertThat(saved.getMemberId()).isEqualTo(100L);
        assertThat(saved.getAmount()).isEqualTo(10_000);
        assertThat(saved.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(saved.getStatus()).isEqualTo(SettlementItemStatus.ACCRUED);
    }

    @Test
    void 이미_적립된_참여_건이면_저장하지_않는다() {
        SettlementAccrualService service = new SettlementAccrualService(settlementItemRepository);
        when(settlementItemRepository.existsByGroupBuyParticipantId(50L)).thenReturn(true);

        service.accrue(message());

        verify(settlementItemRepository, never()).save(any());
    }

    @Test
    void 이미_정산이_확정된_공동구매면_적립하지_않는다() {
        SettlementAccrualService service = new SettlementAccrualService(settlementItemRepository);
        when(settlementItemRepository.existsByGroupBuyParticipantId(50L)).thenReturn(false);
        when(settlementItemRepository.existsByGroupBuyIdAndStatus(42L, SettlementItemStatus.CONFIRMED))
                .thenReturn(true);

        service.accrue(message());

        verify(settlementItemRepository, never()).save(any());
    }

    // exists 확인을 나란히 통과한 동시 중복은 UNIQUE 제약이 잡는다 - 그때 나는 예외를 흡수하고 전파하지 않는다
    @Test
    void 동시_중복으로_UNIQUE_제약이_걸리면_예외를_전파하지_않고_흡수한다() {
        SettlementAccrualService service = new SettlementAccrualService(settlementItemRepository);
        when(settlementItemRepository.existsByGroupBuyParticipantId(50L)).thenReturn(false);
        when(settlementItemRepository.existsByGroupBuyIdAndStatus(42L, SettlementItemStatus.CONFIRMED))
                .thenReturn(false);
        when(settlementItemRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk violation"));

        assertThatCode(() -> service.accrue(message())).doesNotThrowAnyException();
    }
}
