package com.wellbuying.domain.product.search;

import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ProductSearchOutboxRelay가 배치 전체를 ES에 반영한 결과(성공/실패)를 DB에 반영한다.
// 건별 개별 UPDATE를 내보내지 않도록, 성공/실패 각각을 한 번의 벌크 UPDATE로 묶어서 처리한다.
@Component
public class ProductSearchOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchOutboxDispatcher.class);

    private final ProductSearchEventOutboxRepository outboxRepository;

    public ProductSearchOutboxDispatcher(ProductSearchEventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    // 반영 성공 - published_at을 한 번의 UPDATE로 채워, 다음 릴레이 주기의 미반영 조회 대상에서 한꺼번에 빠지게 한다
    @Transactional
    public void markPublished(List<ProductSearchEventOutbox> events) {
        if (events.isEmpty()) {
            return;
        }
        outboxRepository.markPublished(events.stream().map(ProductSearchEventOutbox::getId).toList(),
                LocalDateTime.now());
    }

    // 반영 실패 - retryCount를 한 번의 UPDATE로 증가시킨다. published_at은 그대로 null이라 retryCount가
    // MAX_RETRY_COUNT 미만인 동안 다음 릴레이 주기에 재조회되어 재시도된다(at-least-once).
    // 로그 판단(포기 vs 재시도)만 메모리에서 하고(recordFailure/isRetryExhausted), 실제 반영은 벌크 UPDATE 한 번으로 한다
    @Transactional
    public void recordFailures(List<DispatchFailure> failures) {
        if (failures.isEmpty()) {
            return;
        }
        for (DispatchFailure failure : failures) {
            ProductSearchEventOutbox event = failure.event();
            event.recordFailure();
            if (event.isRetryExhausted()) {
                log.error("검색 인덱스 아웃박스 이벤트 반영 포기(최대 {}회 재시도 초과) - id={}, productId={}, eventType={}",
                        ProductSearchEventOutbox.MAX_RETRY_COUNT, event.getId(), event.getProductId(),
                        event.getEventType(), failure.cause());
            } else {
                log.error("검색 인덱스 아웃박스 이벤트 반영 실패({}번째 재시도) - id={}, productId={}, eventType={}",
                        event.getRetryCount(), event.getId(), event.getProductId(), event.getEventType(),
                        failure.cause());
            }
        }
        outboxRepository.incrementRetryCount(failures.stream().map(f -> f.event().getId()).toList());
    }

    public record DispatchFailure(ProductSearchEventOutbox event, Throwable cause) {
    }
}
