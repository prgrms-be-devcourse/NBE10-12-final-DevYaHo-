package com.wellbuying.domain.product.search;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// pending/dead 두 Gauge가 항상 같은 DB 스냅샷을 읽도록 AtomicReference<OutboxStatusCount>를
// @Scheduled로 갱신한다. scrape마다 DB 쿼리 2회 → 15초마다 1회로 줄고, 지표 간 불일치 없음.
// 생성자에서 DB를 조회하지 않음 - 기동 시 DB 장애/락으로 컨텍스트 로딩이 실패하는 것을 방지.
@Component
public class ProductSearchOutboxMetrics {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchOutboxMetrics.class);

    private final ProductSearchEventOutboxRepository outboxRepository;
    private final AtomicReference<OutboxStatusCount> snapshot =
            new AtomicReference<>(OutboxStatusCount.empty());

    public ProductSearchOutboxMetrics(MeterRegistry meterRegistry,
            ProductSearchEventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        Gauge.builder("wellbuying.search.outbox.events", snapshot, s -> s.get().pending())
                .description("검색 outbox 이벤트 수 (status: pending=미반영, dead=재시도 한도 초과)")
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("wellbuying.search.outbox.events", snapshot, s -> s.get().dead())
                .description("검색 outbox 이벤트 수 (status: pending=미반영, dead=재시도 한도 초과)")
                .tag("status", "dead")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 1_000)
    public void refresh() {
        try {
            snapshot.set(outboxRepository.countStatusSnapshot(ProductSearchEventOutbox.MAX_RETRY_COUNT));
        } catch (Exception e) {
            log.warn("검색 outbox 상태 지표 갱신 실패 (이전 값 유지)", e);
        }
    }
}