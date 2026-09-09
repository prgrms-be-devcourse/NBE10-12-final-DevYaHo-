package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSearchOutboxMetricsTest {

    @Mock
    private ProductSearchEventOutboxRepository outboxRepository;

    @Test
    void refresh_호출_시_pending과_dead_게이지가_스냅샷_값을_반환한다() {
        when(outboxRepository.countStatusSnapshot(ProductSearchEventOutbox.MAX_RETRY_COUNT))
                .thenReturn(new OutboxStatusCount(5, 2));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductSearchOutboxMetrics metrics = new ProductSearchOutboxMetrics(registry, outboxRepository);

        metrics.refresh();

        assertThat(registry.get("wellbuying.search.outbox.events").tag("status", "pending").gauge().value()).isEqualTo(5.0);
        assertThat(registry.get("wellbuying.search.outbox.events").tag("status", "dead").gauge().value()).isEqualTo(2.0);
    }
}