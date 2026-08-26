package com.wellbuying.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.domain.product.dto.ProductSearchCondition;
import java.util.List;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.entity.ProductCount;
import com.wellbuying.domain.product.entity.ProductSortType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductQueryRepositoryTest {

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
                        "INSERT INTO members (id, email, name, role, created_at, updated_at) VALUES "
                                + "(:id, 'seller-test@wellbuying.com', '테스트판매자', 'BUYER', now(), now())")
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
        productRepository.save(laptop);
        Product phone = Product.register(TEST_SELLER_ID, otherCategoryId, "휴대폰A", "설명", 800000, "url");
        phone.approve();
        productRepository.save(phone);

        ProductSearchCondition condition = new ProductSearchCondition(testCategoryId, null, null, ProductSortType.LATEST);
        Slice<ProductSummaryResponse> result = productRepository.search(condition, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting("productName")
                .contains("노트북A")
                .doesNotContain("휴대폰A");
    }

    // 가격 범위를 지정하면 범위 밖의 상품은 결과에서 제외된다
    @Test
    void search_가격범위를_지정하면_범위밖_상품은_제외된다() {
        Product cheapOutOfRange = Product.register(TEST_SELLER_ID, testCategoryId, "범위밖저가", "설명", 5000, "url");
        cheapOutOfRange.approve();
        productRepository.save(cheapOutOfRange);
        Product inRange = Product.register(TEST_SELLER_ID, testCategoryId, "범위안상품", "설명", 50000, "url");
        inRange.approve();
        productRepository.save(inRange);
        Product expensiveOutOfRange = Product.register(TEST_SELLER_ID, testCategoryId, "범위밖고가", "설명", 500000, "url");
        expensiveOutOfRange.approve();
        productRepository.save(expensiveOutOfRange);

        ProductSearchCondition condition = new ProductSearchCondition(null, 10000, 100000, ProductSortType.LATEST);
        Slice<ProductSummaryResponse> result = productRepository.search(condition, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting("productName")
                .contains("범위안상품")
                .doesNotContain("범위밖저가", "범위밖고가");
    }

    // APPROVED 상품만 목록에 나오고, PENDING/REJECTED 상품은 제외된다
    @Test
    void search_승인대기_또는_거절된_상품은_목록에서_제외된다() {
        Product approved = Product.register(TEST_SELLER_ID, testCategoryId, "승인된상품", "설명", 10000, "url");
        approved.approve();
        productRepository.save(approved);
        productRepository.save(Product.register(TEST_SELLER_ID, testCategoryId, "대기중상품", "설명", 10000, "url"));
        Product rejected = Product.register(TEST_SELLER_ID, testCategoryId, "거절된상품", "설명", 10000, "url");
        rejected.reject();
        productRepository.save(rejected);

        ProductSearchCondition condition = new ProductSearchCondition(null, null, null, ProductSortType.LATEST);
        Slice<ProductSummaryResponse> result = productRepository.search(condition, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting("productName")
                .contains("승인된상품")
                .doesNotContain("대기중상품", "거절된상품");
    }

    // 인기순(POPULAR) 정렬 시 조회수(viewCount)가 높은 상품이 먼저 나온다
    @Test
    void search_인기순_정렬시_조회수높은_상품이_먼저나온다() {
        Product lowView = Product.register(TEST_SELLER_ID, testCategoryId, "인기적은상품", "설명", 10000, "url");
        lowView.approve();
        lowView = productRepository.save(lowView);
        Product highView = Product.register(TEST_SELLER_ID, testCategoryId, "인기많은상품", "설명", 10000, "url");
        highView.approve();
        highView = productRepository.save(highView);
        productCountRepository.save(withViewCount(lowView.getId(), 5L));
        productCountRepository.save(withViewCount(highView.getId(), 500L));

        ProductSearchCondition condition = new ProductSearchCondition(null, null, null, ProductSortType.POPULAR);
        Slice<ProductSummaryResponse> result = productRepository.search(condition, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).productName()).isEqualTo("인기많은상품");
    }

    // 인기순(POPULAR) 정렬 시 ProductCount가 없어 viewCount가 NULL인 상품은 맨 뒤로 밀린다
    @Test
    void search_인기순_정렬시_조회수_없는_상품은_맨_뒤로_간다() {
        Product noCount = Product.register(TEST_SELLER_ID, testCategoryId, "조회수없는상품", "설명", 10000, "url");
        noCount.approve();
        noCount = productRepository.save(noCount);
        Product withCount = Product.register(TEST_SELLER_ID, testCategoryId, "조회수있는상품", "설명", 10000, "url");
        withCount.approve();
        withCount = productRepository.save(withCount);
        productCountRepository.save(withViewCount(withCount.getId(), 10L));
        // noCount는 ProductCount 저장 안 함 → LEFT JOIN 후 viewCount = NULL

        ProductSearchCondition condition = new ProductSearchCondition(null, null, null, ProductSortType.POPULAR);
        Slice<ProductSummaryResponse> result = productRepository.search(condition, PageRequest.of(0, 20));

        List<String> names = result.getContent().stream().map(ProductSummaryResponse::productName).toList();
        assertThat(names.indexOf("조회수있는상품")).isLessThan(names.indexOf("조회수없는상품"));
    }

    // 가격 오름차순(PRICE_ASC) 정렬 시 저렴한 상품이 먼저 나온다
    @Test
    void search_가격오름차순_정렬시_저렴한상품이_먼저나온다() {
        Product expensive = Product.register(TEST_SELLER_ID, testCategoryId, "비싼상품", "설명", 90000, "url");
        expensive.approve();
        productRepository.save(expensive);
        Product cheap = Product.register(TEST_SELLER_ID, testCategoryId, "저렴한상품", "설명", 10000, "url");
        cheap.approve();
        productRepository.save(cheap);

        ProductSearchCondition condition = new ProductSearchCondition(null, null, null, ProductSortType.PRICE_ASC);
        Slice<ProductSummaryResponse> result = productRepository.search(condition, PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).productName()).isEqualTo("저렴한상품");
    }

    // 요청한 페이지 크기보다 데이터가 많으면 hasNext가 true다
    @Test
    void search_페이지크기보다_데이터가_많으면_hasNext가_true다() {
        for (int i = 0; i < 5; i++) {
            Product p = Product.register(TEST_SELLER_ID, testCategoryId, "상품" + i, "설명", 10000, "url");
            p.approve();
            productRepository.save(p);
        }

        ProductSearchCondition condition = new ProductSearchCondition(null, null, null, ProductSortType.LATEST);
        Slice<ProductSummaryResponse> result = productRepository.search(condition, PageRequest.of(0, 3));

        assertThat(result.hasNext()).isTrue();
        assertThat(result.getContent()).hasSize(3);
    }

    private ProductCount withViewCount(Long productId, long viewCount) {
        ProductCount count = ProductCount.init(productId);
        for (long i = 0; i < viewCount; i++) {
            count.increaseViewCount();
        }
        return count;
    }
}
