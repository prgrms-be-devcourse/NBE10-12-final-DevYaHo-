package com.wellbuying.domain.settlement.dto;

import java.time.LocalDateTime;

// COMPLETED 정산 건 상세 - 결제한 참여자 한 명의 내역
public record SettlementParticipantResponse(
        Long memberId,
        String memberName,
        int amount,
        LocalDateTime paidAt
) {
}
