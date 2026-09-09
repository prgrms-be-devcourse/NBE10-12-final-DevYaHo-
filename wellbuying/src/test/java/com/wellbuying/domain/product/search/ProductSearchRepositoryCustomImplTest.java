package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.product.search.ProductSearchFilter;
import com.wellbuying.global.dto.CursorPageResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

// Testcontainers OpenSearch(Nori 플러그인 포함 커스텀 이미지)를 AbstractIntegrationTest에서 기동해 사용한다.
class ProductSearchRepositoryCustomImplTest extends AbstractIntegrationTest {

    @Autowired
    private ProductSearchRepository productSearchRepository;

    @Autowired
    private ElasticsearchOperations operations;

    // 테스트 격리를 위해 식별하기 쉬운 ID 범위를 사용
    private static final long TEST_ID_BASE = 900_000_000L;

    @BeforeEach
    void setUp() {
        productSearchRepository.saveAll(List.of(
                doc(TEST_ID_BASE + 1, "유기농 비타민C 1000mg", "면역력 강화에 도움", "APPROVED"),
                doc(TEST_ID_BASE + 2, "프리미엄 오메가3", "비타민D와 함께 복용 권장", "APPROVED"),
                doc(TEST_ID_BASE + 3, "콜라겐 파우더", "피부 탄력 개선", "PENDING"),
                doc(TEST_ID_BASE + 4, "마그네슘 400mg", "수면 개선 보조제", "REJECTED")
        ));
        // 인덱싱 후 즉시 검색 가능하도록 강제 refresh
        operations.indexOps(ProductSearchDocument.class).refresh();
    }

    @AfterEach
    void cleanUp() {
        productSearchRepository.deleteAllById(List.of(
                TEST_ID_BASE + 1, TEST_ID_BASE + 2, TEST_ID_BASE + 3, TEST_ID_BASE + 4
        ));
    }

    @Test
    void search_키워드가_productName에_포함된_APPROVED_상품을_반환한다() {
        // "유기농 비타민C"는 성능테스트 데이터에 없는 조합 — 관련도 오염 방지
        CursorPageResponse<ProductSearchResponse> result =
                productSearchRepository.search("유기농 비타민C", null, 20, ProductSearchFilter.none());

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        assertThat(names).contains("유기농 비타민C 1000mg");
    }

    @Test
    void search_키워드가_description에만_있어도_반환된다() {
        CursorPageResponse<ProductSearchResponse> result =
                productSearchRepository.search("비타민", null, 20, ProductSearchFilter.none());

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        // 오메가3 상품은 productName에 '비타민'이 없지만 description에 '비타민D'가 있어 매칭돼야 한다
        assertThat(names).contains("프리미엄 오메가3");
    }

    @Test
    void search_PENDING_상품은_결과에서_제외된다() {
        CursorPageResponse<ProductSearchResponse> result =
                productSearchRepository.search("콜라겐", null, 20, ProductSearchFilter.none());

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        assertThat(names).doesNotContain("콜라겐 파우더");
    }

    @Test
    void search_REJECTED_상품은_결과에서_제외된다() {
        CursorPageResponse<ProductSearchResponse> result =
                productSearchRepository.search("마그네슘", null, 20, ProductSearchFilter.none());

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        assertThat(names).doesNotContain("마그네슘 400mg");
    }

    // size+1개 준비 → 1차 hasNext=true + nextCursor 수령 → 2차 요청 → 결과 겹침 없음 확인
    @Test
    void search_커서로_다음_페이지를_조회하면_결과가_겹치지_않는다() {
        // 비타민 키워드로 APPROVED 문서 2건이 매칭되므로 size=1이면 hasNext=true
        CursorPageResponse<ProductSearchResponse> first =
                productSearchRepository.search("비타민", null, 1, ProductSearchFilter.none());

        assertThat(first.hasNext()).isTrue();
        assertThat(first.content()).hasSize(1);
        assertThat(first.nextCursor()).isNotNull();

        CursorPageResponse<ProductSearchResponse> second =
                productSearchRepository.search("비타민", first.nextCursor(), 1, ProductSearchFilter.none());

        assertThat(second.content()).isNotEmpty();
        assertThat(second.content().get(0).productName())
                .isNotEqualTo(first.content().get(0).productName());
    }

    @Test
    void search_activeGroupBuyOnly가_true면_hasActiveGroupBuy가_true인_상품만_반환한다() {
        long id = TEST_ID_BASE + 5;
        productSearchRepository.save(doc(id, "액티브공동구매상품", "진행중", "APPROVED", true));
        operations.indexOps(ProductSearchDocument.class).refresh();
        try {
            CursorPageResponse<ProductSearchResponse> result =
                    productSearchRepository.search("액티브공동구매상품", null, 20, new ProductSearchFilter(null, null, null, true));

            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).hasActiveGroupBuy()).isTrue();
        } finally {
            productSearchRepository.deleteById(id);
        }
    }

    @Test
    void search_categoryId_필터로_해당_카테고리_상품만_반환한다() {
        long idA = TEST_ID_BASE + 10, idB = TEST_ID_BASE + 11;
        productSearchRepository.saveAll(List.of(
                doc(idA, "카테고리테스트A", "카테고리 필터 검증", "APPROVED", 1L),
                doc(idB, "카테고리테스트B", "카테고리 필터 검증", "APPROVED", 2L)));
        operations.indexOps(ProductSearchDocument.class).refresh();
        try {
            CursorPageResponse<ProductSearchResponse> result =
                    productSearchRepository.search("카테고리테스트", null, 20, new ProductSearchFilter(2L, null, null, false));
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).id()).isEqualTo(idB);
        } finally {
            productSearchRepository.deleteAllById(List.of(idA, idB));
        }
    }

    @Test
    void search_가격대_필터로_해당_범위_상품만_반환한다() {
        long id5k = TEST_ID_BASE + 20, id15k = TEST_ID_BASE + 21, id30k = TEST_ID_BASE + 22;
        productSearchRepository.saveAll(List.of(
                docWithPrice(id5k,  "가격대테스트A", "가격 필터 검증", "APPROVED", 5000),
                docWithPrice(id15k, "가격대테스트B", "가격 필터 검증", "APPROVED", 15000),
                docWithPrice(id30k, "가격대테스트C", "가격 필터 검증", "APPROVED", 30000)));
        operations.indexOps(ProductSearchDocument.class).refresh();
        try {
            CursorPageResponse<ProductSearchResponse> result =
                    productSearchRepository.search("가격대테스트", null, 20, new ProductSearchFilter(null, 10000, 20000, false));
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).id()).isEqualTo(id15k);
        } finally {
            productSearchRepository.deleteAllById(List.of(id5k, id15k, id30k));
        }
    }

    private ProductSearchDocument doc(long id, String name, String description, String status) {
        return new ProductSearchDocument(id, name, description, 1L, status, 10000, 0L, "url", 1L, LocalDateTime.now(),
                false, null, null, null, null, null, null, null);
    }

    private ProductSearchDocument doc(long id, String name, String description, String status, long categoryId) {
        return new ProductSearchDocument(id, name, description, categoryId, status, 10000, 0L, "url", 1L, LocalDateTime.now(),
                false, null, null, null, null, null, null, null);
    }

    private ProductSearchDocument docWithPrice(long id, String name, String description, String status, int startPrice) {
        return new ProductSearchDocument(id, name, description, 1L, status, startPrice, 0L, "url", 1L, LocalDateTime.now(),
                false, null, null, null, null, null, null, null);
    }

    private ProductSearchDocument doc(long id, String name, String description, String status, boolean hasActiveGroupBuy) {
        return new ProductSearchDocument(id, name, description, 1L, status, 10000, 0L, "url", 1L, LocalDateTime.now(),
                hasActiveGroupBuy,
                hasActiveGroupBuy ? 100L : null,
                hasActiveGroupBuy ? "ONGOING" : null,
                hasActiveGroupBuy ? 8000 : null,
                hasActiveGroupBuy ? 5 : null,
                hasActiveGroupBuy ? 10 : null,
                hasActiveGroupBuy ? 100 : null,
                hasActiveGroupBuy ? LocalDateTime.of(2026, 9, 30, 23, 59) : null);
    }
}
