package com.wellbuying.domain.product.search;

import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 아웃박스에 쌓인 미반영 이벤트를 주기적으로 폴링해 OpenSearch 인덱스에 반영한다.
// 상품 상태 변경과 아웃박스 기록은 같은 트랜잭션으로 묶여 있으므로,
// 여기서 ES 반영이 잠시 실패하거나 프로세스가 죽어도 이벤트는 DB에 남아 다음 주기에 재시도된다.
// OpenSearch 호출은 동기 HTTP이므로 GroupBuyOutboxRelay와 달리 CompletableFuture 병렬화 없이
// 순차 처리한다 - 배치당 최대 200건, 건당 수십 ms 수준이라 3초 폴링 주기 안에 충분히 완료된다
@Component
public class ProductSearchOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchOutboxRelay.class);

    private static final Limit BATCH_LIMIT = Limit.of(200);

    private final ProductSearchEventOutboxRepository outboxRepository;
    private final ProductSearchOutboxDispatcher dispatcher;
    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;

    public ProductSearchOutboxRelay(ProductSearchEventOutboxRepository outboxRepository,
            ProductSearchOutboxDispatcher dispatcher,
            ProductRepository productRepository,
            ProductSearchRepository productSearchRepository) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.productRepository = productRepository;
        this.productSearchRepository = productSearchRepository;
    }

    // 조회/ES 반영/DB 반영 중 예외가 나면(DB 커넥션 문제 등) 전체를 잡아 로그만 남기고
    // 다음 3초 주기를 기약한다 - 건별 ES 실패는 applyToIndex 내부 try/catch에서 DispatchFailure로
    // 감싸두었으므로 여기서 잡을 예외는 그 바깥의 인프라성 문제뿐이다
    @Scheduled(fixedDelay = 3_000)
    public void relay() {
        try {
            List<ProductSearchEventOutbox> pending =
                    outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(
                            ProductSearchEventOutbox.MAX_RETRY_COUNT, BATCH_LIMIT);
            if (pending.isEmpty()) {
                return;
            }

            List<ProductSearchEventOutbox> succeeded = new ArrayList<>();
            List<ProductSearchOutboxDispatcher.DispatchFailure> failures = new ArrayList<>();

            for (ProductSearchEventOutbox event : pending) {
                try {
                    applyToIndex(event);
                    succeeded.add(event);
                } catch (Exception e) {
                    failures.add(new ProductSearchOutboxDispatcher.DispatchFailure(event, e));
                }
            }

            dispatcher.markPublished(succeeded);
            dispatcher.recordFailures(failures);
        } catch (Exception e) {
            log.error("검색 인덱스 아웃박스 릴레이 작업 중 예외 발생", e);
        }
    }

    // UPSERT: 폴링 시점에 최신 상품을 재조회해 인덱스에 반영한다.
    // 재조회 결과가 없으면(그새 삭제된 상품) DELETE와 동일하게 인덱스에서 제거한다
    private void applyToIndex(ProductSearchEventOutbox event) {
        if ("DELETE".equals(event.getEventType())) {
            productSearchRepository.deleteById(event.getProductId());
            return;
        }
        Optional<Product> product = productRepository.findById(event.getProductId());
        if (product.isPresent()) {
            productSearchRepository.save(ProductSearchDocument.of(product.get()));
        } else {
            productSearchRepository.deleteById(event.getProductId());
        }
    }
}
