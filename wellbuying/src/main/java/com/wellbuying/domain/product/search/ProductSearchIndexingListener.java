package com.wellbuying.domain.product.search;

import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// RDB 트랜잭션 커밋 완료 후 별도 스레드에서 OpenSearch 인덱스를 동기화
// - AFTER_COMMIT: RDB 커밋이 확정된 시점에 실행되므로 OpenSearch와 트랜잭션 경계를 공유하지 않음
// - @Async: AFTER_COMMIT 콜백 스레드를 점유하지 않도록 전용 풀로 위임
@Component
public class ProductSearchIndexingListener {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchIndexingListener.class);

    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;

    public ProductSearchIndexingListener(ProductRepository productRepository,
                                         ProductSearchRepository productSearchRepository) {
        this.productRepository = productRepository;
        this.productSearchRepository = productSearchRepository;
    }

    @Async("searchIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductSearchDataChanged(ProductSearchDataChangedEvent event) {
        try {
            Product product = productRepository.findById(event.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
            productSearchRepository.save(ProductSearchDocument.of(product));
        } catch (Exception e) {
            log.error("검색 인덱스 동기화 실패: productId={}", event.productId(), e);
        }
    }
}
