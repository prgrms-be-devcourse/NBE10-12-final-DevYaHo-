package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wellbuying.global.dto.CursorPageResponse;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

// 로컬 OpenSearch(localhost:9200)에 실제 문서를 인덱싱하여 검색 쿼리를 검증한다.
// 실행 전 OpenSearch가 기동 중이어야 하며 Nori 플러그인이 설치되어 있어야 한다.
@Tag("integration")
@SpringBootTest
class ProductSearchRepositoryCustomImplTest {

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
                productSearchRepository.search("유기농 비타민C", null, 20);

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        assertThat(names).contains("유기농 비타민C 1000mg");
    }

    @Test
    void search_키워드가_description에만_있어도_반환된다() {
        CursorPageResponse<ProductSearchResponse> result =
                productSearchRepository.search("비타민", null, 20);

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        // 오메가3 상품은 productName에 '비타민'이 없지만 description에 '비타민D'가 있어 매칭돼야 한다
        assertThat(names).contains("프리미엄 오메가3");
    }

    @Test
    void search_PENDING_상품은_결과에서_제외된다() {
        CursorPageResponse<ProductSearchResponse> result =
                productSearchRepository.search("콜라겐", null, 20);

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        assertThat(names).doesNotContain("콜라겐 파우더");
    }

    @Test
    void search_REJECTED_상품은_결과에서_제외된다() {
        CursorPageResponse<ProductSearchResponse> result =
                productSearchRepository.search("마그네슘", null, 20);

        List<String> names = result.content().stream().map(ProductSearchResponse::productName).toList();
        assertThat(names).doesNotContain("마그네슘 400mg");
    }

    // size+1개 준비 → 1차 hasNext=true + nextCursor 수령 → 2차 요청 → 결과 겹침 없음 확인
    @Test
    void search_커서로_다음_페이지를_조회하면_결과가_겹치지_않는다() {
        // 비타민 키워드로 APPROVED 문서 2건이 매칭되므로 size=1이면 hasNext=true
        CursorPageResponse<ProductSearchResponse> first =
                productSearchRepository.search("비타민", null, 1);

        assertThat(first.hasNext()).isTrue();
        assertThat(first.content()).hasSize(1);
        assertThat(first.nextCursor()).isNotNull();

        CursorPageResponse<ProductSearchResponse> second =
                productSearchRepository.search("비타민", first.nextCursor(), 1);

        assertThat(second.content()).isNotEmpty();
        assertThat(second.content().get(0).productName())
                .isNotEqualTo(first.content().get(0).productName());
    }

    private ProductSearchDocument doc(long id, String name, String description, String status) {
        return new ProductSearchDocument(id, name, description, 1L, status, 10000, 0L, "url", 1L, LocalDateTime.now());
    }
}
