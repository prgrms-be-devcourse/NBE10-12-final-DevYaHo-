package com.wellbuying.domain.product.search;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record ProductSearchReconcileStatusResponse(
        long lastSuccessTimestamp,
        String lastSuccessAt,
        long resumeFromId,
        int consecutiveFailures
) {
    public static ProductSearchReconcileStatusResponse from(ProductSearchReconcileScheduler scheduler) {
        long ts = scheduler.getLastSuccessTimestamp();
        String lastSuccessAt = ts == 0 ? null
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZoneId.of("Asia/Seoul")).toString();
        return new ProductSearchReconcileStatusResponse(
                ts, lastSuccessAt, scheduler.getResumeFromId(), scheduler.getConsecutiveFailures());
    }
}
