package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.product.dto.CategoryTreeResponse;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CategoryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // 최상위 카테고리 아래에 하위 카테고리가 자식으로 묶여서 트리가 조립된다
    @Test
    void getCategoryTree_부모자식_관계에_맞게_트리로_조립된다() {
        ProductCategory root = categoryRepository.save(ProductCategory.create(null, "전자제품"));
        categoryRepository.save(ProductCategory.create(root.getId(), "노트북"));
        categoryRepository.save(ProductCategory.create(root.getId(), "휴대폰"));

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        CategoryTreeResponse found = tree.stream()
                .filter(t -> t.categoryName().equals("전자제품"))
                .findFirst()
                .orElseThrow();
        assertThat(found.children()).extracting("categoryName")
                .containsExactlyInAnyOrder("노트북", "휴대폰");
    }

    // 데이터가 잘못 들어가서 카테고리끼리 순환 참조가 생겨도, 무한 재귀 없이 안전하게 끝난다
    @Test
    void getCategoryTree_순환참조가_있어도_무한루프에_빠지지_않는다() {
        entityManager.createNativeQuery(
                        "INSERT INTO product_category (id, category_name, parent_id, created_at) VALUES "
                                + "(9001, 'A', 9002, now()), (9002, 'B', 9001, now())")
                .executeUpdate();

        assertThatCode(() -> categoryService.getCategoryTree()).doesNotThrowAnyException();
    }
}
