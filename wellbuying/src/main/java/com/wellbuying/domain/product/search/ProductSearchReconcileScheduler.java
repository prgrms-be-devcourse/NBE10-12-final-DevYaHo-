package com.wellbuying.domain.product.search;

import com.wellbuying.domain.groupbuy.dto.GroupBuyProductSummaryResponse;
import com.wellbuying.domain.groupbuy.service.GroupBuyService;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 검색 인덱스 정합성 보정 배치.
// 공동구매 상태·수량은 상품 변경 없이도 바뀌므로(참여, 마감, 성사/실패) outbox 이벤트만으로는
// 검색 문서의 공동구매 요약이 최신으로 유지되지 않는다. 주기적으로 APPROVED 상품 전체를 재색인해
// 공동구매 요약을 갱신하고, 그 과정에서 PostgreSQL↔OpenSearch 간 어긋난 문서도 함께 보정한다.
// 페이지 단위로 처리해 한 번에 많은 메모리를 쓰지 않으며, 실패해도 다음 주기에 다시 시도한다.
// OFFSET 대신 id 커서로 순차 조회해 count 쿼리와 뒤 페이지 지연을 피한다.
@Component
public class ProductSearchReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchReconcileScheduler.class);
    private static final int PAGE_SIZE = 500;

    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;
    private final GroupBuyService groupBuyService;
    private final Timer reconcileTimer;
    private final Counter reconciledDocuments;

    public ProductSearchReconcileScheduler(ProductRepository productRepository,
            ProductSearchRepository productSearchRepository,
            GroupBuyService groupBuyService,
            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.productSearchRepository = productSearchRepository;
        this.groupBuyService = groupBuyService;
        this.reconcileTimer = Timer.builder("wellbuying.search.reconcile.duration")
                .description("검색 인덱스 정합성 보정 배치 1회 소요 시간").register(meterRegistry);
        this.reconciledDocuments = Counter.builder("wellbuying.search.reconcile.documents")
                .description("보정 배치가 재색인한 누적 문서 수").register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${search.reconcile.fixed-delay-ms:600000}",
            initialDelayString = "${search.reconcile.initial-delay-ms:600000}")
    public void reconcile() {
        Timer.Sample sample = Timer.start();
        long lastId = 0L;
        int total = 0;
        try {
            while (true) {
                List<Product> products = productRepository.findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
                        ProductStatus.APPROVED, lastId, PageRequest.of(0, PAGE_SIZE));
                if (products.isEmpty()) {
                    break;
                }
                List<Long> ids = products.stream().map(Product::getId).toList();
                Map<Long, GroupBuyProductSummaryResponse> summaries =
                        groupBuyService.getActiveSummariesByProductIds(ids);
                List<ProductSearchDocument> documents = products.stream()
                        .map(p -> ProductSearchDocument.of(p, summaries.get(p.getId())))
                        .toList();
                productSearchRepository.saveAll(documents);
                total += documents.size();
                reconciledDocuments.increment(documents.size());
                lastId = products.get(products.size() - 1).getId();
            }
            log.info("검색 인덱스 정합성 보정 완료: {}건 재색인", total);
        } catch (Exception e) {
            log.error("검색 인덱스 정합성 보정 실패: lastId={}, 지금까지 {}건 처리", lastId, total, e);
        } finally {
            sample.stop(reconcileTimer);
        }
    }
}
