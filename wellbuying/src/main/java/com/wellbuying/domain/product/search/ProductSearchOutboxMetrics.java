package com.wellbuying.domain.product.search;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

// 검색 outbox 상태를 Prometheus Gauge로 노출. pending이 계속 늘면 릴레이가 막힌 것,
// dead가 0이 아니면 재시도 한도를 넘긴 이벤트가 있어 수동 확인 필요.
// Gauge는 scrape 시점마다 count 쿼리를 실행하지만 outbox 테이블이 작고 published_at 조건이라 부담 없음.
// status 태그로 pending/dead 구분.
@Component
public class ProductSearchOutboxMetrics {

    public ProductSearchOutboxMetrics(MeterRegistry meterRegistry,
            ProductSearchEventOutboxRepository outboxRepository) {
        Gauge.builder("wellbuying.search.outbox.events", outboxRepository,
                r -> r.countByPublishedAtIsNullAndRetryCountLessThan(ProductSearchEventOutbox.MAX_RETRY_COUNT))
                .description("OpenSearch에 아직 반영되지 않은 검색 outbox 이벤트 수")
                .tag("status", "pending")
                .register(meterRegistry);
        Gauge.builder("wellbuying.search.outbox.events", outboxRepository,
                r -> r.countByPublishedAtIsNullAndRetryCountGreaterThanEqual(ProductSearchEventOutbox.MAX_RETRY_COUNT))
                .description("재시도 한도를 넘어 포기된 검색 outbox 이벤트 수")
                .tag("status", "dead")
                .register(meterRegistry);
    }
}