package com.wellbuying.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.entity.ProductCount;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.global.dto.CursorPageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ProductQueryRepositoryTest extends AbstractIntegrationTest {

    private static final Long TEST_SELLER_ID = 9999L;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCountRepository productCountRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Long testCategoryId;

    // product.seller_id/category_id는 members/product_category를 참조하는 외래키라,
    // 테스트에서도 실제로 존재하는 행이 있어야 함. Member는 다른 트랙 소유라 직접 안 쓰고 네이티브 쿼리로 최소 행만 생성
    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery(
                        "INSERT INTO members (id, email, name, role, status, created_at, updated_at) VALUES "
                                + "(:id, 'seller-test@wellbuying.com', '테스트판매자', 'BUYER', 'ACTIVE', now(), now())")
                .setParameter("id", TEST_SELLER_ID)
                .executeUpdate();

        testCategoryId = categoryRepository.save(ProductCategory.create(null, "테스트카테고리")).getId();
    }

    // 카테고리로 필터링하면 다른 카테고리 상품은 결과에서 제외된다
    @Test
    void search_카테고리로_필터링하면_다른_카테고리_상품은_제외된다() {
        Long otherCategoryId = categoryRepository.save(ProductCategory.create(null, "다른카테고리")).getId();
        Product laptop = Product.register(TEST_SELLER_ID, testCategoryId, "노트북A", "설명", 1000000, "url");
        laptop.approve();
        laptop = productRepository.save(laptop);
        productCountRepository.save(ProductCount.init(laptop.getId()));
        Product phone = Product.register(TEST_SELLER_ID, otherCategoryId, "휴대폰A", "설명", 800000, "url");
        phone.approve();
        phone = productRepository.save(phone);
        productCountRepository.save(ProductCount.init(phone.getId()));

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.LATEST);
        CursorPageResponse<ProductSummaryResponse> result = productRepository.search(condition, null, 20);

        assertThat(result.content()).extracting("productName")
                .contains("노트북A")
                .doesNotContain("휴대폰A");
    }

    // 가격 범위를 지정하면 범위 밖의 상품은 결과에서 제외된다
    @Test
    void search_가격범위를_지정하면_범위밖_상품은_제외된다() {
        Product cheapOutOfRange = Product.register(TEST_SELLER_ID, testCategoryId, "범위밖저가", "설명", 5000, "url");
        cheapOutOfRange.approve();
        cheapOutOfRange = productRepository.save(cheapOutOfRange);
        productCountRepository.save(ProductCount.init(cheapOutOfRange.getId()));
        Product inRange = Product.register(TEST_SELLER_ID, testCategoryId, "범위안상품", "설명", 50000, "url");
        inRange.approve();
        inRange = productRepository.save(inRange);
        productCountRepository.save(ProductCount.init(inRange.getId()));
        Product expensiveOutOfRange = Product.register(TEST_SELLER_ID, testCategoryId, "범위밖고가", "설명", 500000, "url");
        expensiveOutOfRange.approve();
        expensiveOutOfRange = productRepository.save(expensiveOutOfRange);
        productCountRepository.save(ProductCount.init(expensiveOutOfRange.getId()));

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, 10000, 100000, ProductSortType.LATEST);
        CursorPageResponse<ProductSummaryResponse> result = productRepository.search(condition, null, 20);

        assertThat(result.content()).extracting("productName")
                .contains("범위안상품")
                .doesNotContain("범위밖저가", "범위밖고가");
    }

    // APPROVED 상품만 목록에 나오고, PENDING/REJECTED 상품은 제외된다
    @Test
    void search_승인대기_또는_거절된_상품은_목록에서_제외된다() {
        Product approved = Product.register(TEST_SELLER_ID, testCategoryId, "승인된상품", "설명", 10000, "url");
        approved.approve();
        approved = productRepository.save(approved);
        productCountRepository.save(ProductCount.init(approved.getId()));
        Product pending = productRepository.save(Product.register(TEST_SELLER_ID, testCategoryId, "대기중상품", "설명", 10000, "url"));
        productCountRepository.save(ProductCount.init(pending.getId()));
        Product rejected = Product.register(TEST_SELLER_ID, testCategoryId, "거절된상품", "설명", 10000, "url");
        rejected.reject();
        rejected = productRepository.save(rejected);
        productCountRepository.save(ProductCount.init(rejected.getId()));

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.LATEST);
        CursorPageResponse<ProductSummaryResponse> result = productRepository.search(condition, null, 20);

        assertThat(result.content()).extracting("productName")
                .contains("승인된상품")
                .doesNotContain("대기중상품", "거절된상품");
    }

    // 인기순(POPULAR) 정렬 시 조회수(viewCount)가 높은 상품이 먼저 나온다
    @Test
    void search_인기순_정렬시_조회수높은_상품이_먼저나온다() {
        Product lowView = Product.register(TEST_SELLER_ID, testCategoryId, "인기적은상품", "설명", 10000, "url");
        lowView.approve();
        lowView = productRepository.save(lowView);
        productCountRepository.save(withViewCount(lowView.getId(), 5L));
        Product highView = Product.register(TEST_SELLER_ID, testCategoryId, "인기많은상품", "설명", 10000, "url");
        highView.approve();
        highView = productRepository.save(highView);
        productCountRepository.save(withViewCount(highView.getId(), 500L));

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.POPULAR);
        CursorPageResponse<ProductSummaryResponse> result = productRepository.search(condition, null, 20);

        assertThat(result.content().get(0).productName()).isEqualTo("인기많은상품");
    }

    // 인기순(POPULAR) 정렬 시 viewCount=0인 상품은 viewCount가 높은 상품보다 뒤에 나온다
    @Test
    void search_인기순_정렬시_조회수_낮은_상품은_뒤로_간다() {
        Product zeroView = Product.register(TEST_SELLER_ID, testCategoryId, "조회수없는상품", "설명", 10000, "url");
        zeroView.approve();
        zeroView = productRepository.save(zeroView);
        productCountRepository.save(ProductCount.init(zeroView.getId())); // viewCount = 0
        Product highView = Product.register(TEST_SELLER_ID, testCategoryId, "조회수있는상품", "설명", 10000, "url");
        highView.approve();
        highView = productRepository.save(highView);
        productCountRepository.save(withViewCount(highView.getId(), 10L));

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.POPULAR);
        CursorPageResponse<ProductSummaryResponse> result = productRepository.search(condition, null, 20);

        List<String> names = result.content().stream().map(ProductSummaryResponse::productName).toList();
        assertThat(names.indexOf("조회수있는상품")).isLessThan(names.indexOf("조회수없는상품"));
    }

    // 가격 오름차순(PRICE_ASC) 정렬 시 저렴한 상품이 먼저 나온다
    @Test
    void search_가격오름차순_정렬시_저렴한상품이_먼저나온다() {
        Product expensive = Product.register(TEST_SELLER_ID, testCategoryId, "비싼상품", "설명", 90000, "url");
        expensive.approve();
        expensive = productRepository.save(expensive);
        productCountRepository.save(ProductCount.init(expensive.getId()));
        Product cheap = Product.register(TEST_SELLER_ID, testCategoryId, "저렴한상품", "설명", 10000, "url");
        cheap.approve();
        cheap = productRepository.save(cheap);
        productCountRepository.save(ProductCount.init(cheap.getId()));

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.PRICE_ASC);
        CursorPageResponse<ProductSummaryResponse> result = productRepository.search(condition, null, 20);

        assertThat(result.content().get(0).productName()).isEqualTo("저렴한상품");
    }

    // size+1개 준비 → 1차 요청 hasNext=true + nextCursor 수령 → 2차 요청 → 결과 겹침 없음 확인
    @Test
    void search_커서로_다음_페이지를_조회하면_결과가_겹치지_않는다() {
        for (int i = 0; i < 4; i++) {
            Product p = Product.register(TEST_SELLER_ID, testCategoryId, "상품" + i, "설명", 10000, "url");
            p.approve();
            p = productRepository.save(p);
            productCountRepository.save(ProductCount.init(p.getId()));
        }

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.LATEST);
        CursorPageResponse<ProductSummaryResponse> first = productRepository.search(condition, null, 3);

        assertThat(first.hasNext()).isTrue();
        assertThat(first.content()).hasSize(3);

        CursorPageResponse<ProductSummaryResponse> second = productRepository.search(condition, first.nextCursor(), 3);
        assertThat(second.content()).isNotEmpty();

        List<String> firstNames = first.content().stream().map(ProductSummaryResponse::productName).toList();
        List<String> secondNames = second.content().stream().map(ProductSummaryResponse::productName).toList();
        assertThat(firstNames).doesNotContainAnyElementsOf(secondNames);
    }

    // POPULAR 정렬 2차 조회에서 viewCount=0 상품이 누락되지 않아야 한다
    @Test
    void search_POPULAR_2차_조회에서_viewCount_낮은_상품이_포함된다() {
        // viewCount 있는 상품 3개 (size=2 → 1차에 2개, 2차에 1개 이상 기대)
        Product p1 = Product.register(TEST_SELLER_ID, testCategoryId, "유뷰상품1", "설명", 10000, "url");
        p1.approve();
        p1 = productRepository.save(p1);
        productCountRepository.save(withViewCount(p1.getId(), 9L));

        Product p2 = Product.register(TEST_SELLER_ID, testCategoryId, "유뷰상품2", "설명", 10000, "url");
        p2.approve();
        p2 = productRepository.save(p2);
        productCountRepository.save(withViewCount(p2.getId(), 8L));

        Product p3 = Product.register(TEST_SELLER_ID, testCategoryId, "유뷰상품3", "설명", 10000, "url");
        p3.approve();
        p3 = productRepository.save(p3);
        productCountRepository.save(withViewCount(p3.getId(), 7L));

        // viewCount=0 → POPULAR 정렬 맨 뒤 (모든 상품은 ProductCount 보장)
        Product zeroView = Product.register(TEST_SELLER_ID, testCategoryId, "조회수없는상품", "설명", 10000, "url");
        zeroView.approve();
        zeroView = productRepository.save(zeroView);
        productCountRepository.save(ProductCount.init(zeroView.getId()));

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.POPULAR);
        CursorPageResponse<ProductSummaryResponse> first = productRepository.search(condition, null, 2);

        assertThat(first.hasNext()).isTrue();
        assertThat(first.content()).extracting("productName").doesNotContain("조회수없는상품");

        CursorPageResponse<ProductSummaryResponse> second = productRepository.search(condition, first.nextCursor(), 10);
        assertThat(second.content()).extracting("productName").contains("조회수없는상품");
    }

    private ProductCount withViewCount(Long productId, long viewCount) {
        ProductCount count = ProductCount.init(productId);
        for (long i = 0; i < viewCount; i++) {
            count.increaseViewCount();
        }
        return count;
    }
}
