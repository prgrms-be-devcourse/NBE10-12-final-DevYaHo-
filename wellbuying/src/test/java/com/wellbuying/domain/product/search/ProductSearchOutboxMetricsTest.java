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
    void pending_게이지가_리포지토리_count를_반환한다() {
        when(outboxRepository.countByPublishedAtIsNullAndRetryCountLessThan(ProductSearchEventOutbox.MAX_RETRY_COUNT))
                .thenReturn(5L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ProductSearchOutboxMetrics(registry, outboxRepository);

        assertThat(registry.get("wellbuying.search.outbox.pending").gauge().value()).isEqualTo(5.0);
    }

    @Test
    void dead_게이지가_리포지토리_count를_반환한다() {
        when(outboxRepository.countByPublishedAtIsNullAndRetryCountGreaterThanEqual(ProductSearchEventOutbox.MAX_RETRY_COUNT))
                .thenReturn(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ProductSearchOutboxMetrics(registry, outboxRepository);

        assertThat(registry.get("wellbuying.search.outbox.dead").gauge().value()).isEqualTo(2.0);
    }
}
