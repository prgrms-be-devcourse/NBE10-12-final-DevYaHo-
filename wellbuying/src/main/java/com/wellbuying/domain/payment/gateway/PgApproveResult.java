package com.wellbuying.domain.payment.gateway;

import java.time.LocalDateTime;

// PG 승인 성공 응답 (실패는 PgApprovalException으로 전달된다)
public record PgApproveResult(String pgTransactionId, LocalDateTime approvedAt) {
}
