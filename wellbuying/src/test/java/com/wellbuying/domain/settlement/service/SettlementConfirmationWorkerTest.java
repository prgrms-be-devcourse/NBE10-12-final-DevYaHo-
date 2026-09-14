package com.wellbuying.domain.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import com.wellbuying.domain.settlement.repository.SettlementItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SettlementConfirmationWorkerTest {

    private final SettlementItemRepository settlementItemRepository = mock(SettlementItemRepository.class);
    private final SettlementConfirmationService confirmationService = mock(SettlementConfirmationService.class);
    private final SettlementConfirmationWorker worker =
            new SettlementConfirmationWorker(settlementItemRepository, confirmationService, 3);

    @Test
    void 확정_대상_공동구매마다_확정을_위임한다() {
        when(settlementItemRepository.findGroupBuyIdsReadyToConfirm(eq(SettlementItemStatus.ACCRUED),
                any(LocalDateTime.class), any())).thenReturn(List.of(1L, 2L, 3L));

        worker.confirmDueSettlements();

        verify(confirmationService).confirm(1L);
        verify(confirmationService).confirm(2L);
        verify(confirmationService).confirm(3L);
    }

    @Test
    void 확정_대상이_없으면_아무것도_하지_않는다() {
        when(settlementItemRepository.findGroupBuyIdsReadyToConfirm(any(), any(), any())).thenReturn(List.of());

        worker.confirmDueSettlements();

        verify(confirmationService, never()).confirm(any());
    }

    @Test
    void 한_건이_실패해도_나머지_확정은_계속하고_예외를_전파하지_않는다() {
        when(settlementItemRepository.findGroupBuyIdsReadyToConfirm(any(), any(), any()))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("boom")).when(confirmationService).confirm(2L);

        assertThatCode(worker::confirmDueSettlements).doesNotThrowAnyException();

        verify(confirmationService, times(1)).confirm(1L);
        verify(confirmationService, times(1)).confirm(2L);
        verify(confirmationService, times(1)).confirm(3L);
    }

    @Test
    void 유예기간_일수만큼_이전_시각을_기준으로_대상을_조회한다() {
        when(settlementItemRepository.findGroupBuyIdsReadyToConfirm(any(), any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now().minusDays(3);

        worker.confirmDueSettlements();

        LocalDateTime after = LocalDateTime.now().minusDays(3);
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(settlementItemRepository).findGroupBuyIdsReadyToConfirm(eq(SettlementItemStatus.ACCRUED),
                thresholdCaptor.capture(), any());
        assertThat(thresholdCaptor.getValue()).isBetween(before, after);
    }
}
