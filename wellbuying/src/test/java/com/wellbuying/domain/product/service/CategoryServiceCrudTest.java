package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.product.dto.CategoryCreateRequest;
import com.wellbuying.domain.product.dto.CategoryResponse;
import com.wellbuying.domain.product.dto.CategoryUpdateRequest;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CategoryServiceCrudTest extends AbstractIntegrationTest {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    // ── create ─────────────────────────────────────────────────────────────────

    // parentId가 null이면 최상위(1뎁스) 카테고리로 생성된다
    @Test
    void create_최상위_카테고리_생성_성공() {
        CategoryResponse response = categoryService.create(new CategoryCreateRequest(null, "식품", 1));

        assertThat(response.id()).isNotNull();
        assertThat(response.parentId()).isNull();
        assertThat(response.categoryName()).isEqualTo("식품");
    }

    // parentId가 있는 1뎁스 카테고리 아래에 하위(2뎁스) 카테고리를 생성할 수 있다
    @Test
    void create_하위_카테고리_생성_성공() {
        ProductCategory root = categoryRepository.save(ProductCategory.create(null, "식품", 1));

        CategoryResponse response = categoryService.create(new CategoryCreateRequest(root.getId(), "과일", 1));

        assertThat(response.parentId()).isEqualTo(root.getId());
        assertThat(response.categoryName()).isEqualTo("과일");
    }

    // 존재하지 않는 parentId로 하위 카테고리를 생성하면 PARENT_CATEGORY_NOT_FOUND 예외
    @Test
    void create_존재하지_않는_parentId_예외() {
        assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest(99999L, "과일", 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PARENT_CATEGORY_NOT_FOUND));
    }

    // 2뎁스 카테고리(parentId가 있는 카테고리) 아래에 추가 생성 시도 → CATEGORY_DEPTH_EXCEEDED
    @Test
    void create_3뎁스_생성_시_예외() {
        ProductCategory root = categoryRepository.save(ProductCategory.create(null, "식품", 1));
        ProductCategory child = categoryRepository.save(ProductCategory.create(root.getId(), "과일", 1));

        assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest(child.getId(), "사과", 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CATEGORY_DEPTH_EXCEEDED));
    }

    // 같은 최상위 레벨에 이미 같은 이름이 있으면 CATEGORY_NAME_DUPLICATE
    @Test
    void create_최상위_카테고리_이름_중복_예외() {
        categoryRepository.save(ProductCategory.create(null, "식품", 1));

        assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest(null, "식품", 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATE));
    }

    // 같은 부모 아래 이미 같은 이름이 있으면 CATEGORY_NAME_DUPLICATE
    @Test
    void create_하위_카테고리_이름_중복_예외() {
        ProductCategory root = categoryRepository.save(ProductCategory.create(null, "식품", 1));
        categoryRepository.save(ProductCategory.create(root.getId(), "과일", 1));

        assertThatThrownBy(() -> categoryService.create(new CategoryCreateRequest(root.getId(), "과일", 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CATEGORY_NAME_DUPLICATE));
    }

    // 다른 부모 아래라면 같은 이름이 허용된다
    @Test
    void create_다른_부모_아래_같은_이름_허용() {
        ProductCategory food = categoryRepository.save(ProductCategory.create(null, "식품", 1));
        ProductCategory living = categoryRepository.save(ProductCategory.create(null, "생활용품", 2));
        categoryRepository.save(ProductCategory.create(food.getId(), "기타", 1));

        // "기타"가 다른 부모(생활용품) 아래에는 추가 가능해야 한다
        CategoryResponse response = categoryService.create(new CategoryCreateRequest(living.getId(), "기타", 1));
        assertThat(response.categoryName()).isEqualTo("기타");
        assertThat(response.parentId()).isEqualTo(living.getId());
    }

    // ── update ─────────────────────────────────────────────────────────────────

    // 정상적으로 카테고리명을 변경할 수 있다
    @Test
    void update_카테고리명_변경_성공() {
        ProductCategory category = categoryRepository.save(ProductCategory.create(null, "식품", 1));

        CategoryResponse response = categoryService.update(category.getId(), new CategoryUpdateRequest("신선식품", 1));

        assertThat(response.categoryName()).isEqualTo("신선식품");
    }

    // 존재하지 않는 id로 수정 시도 → CATEGORY_NOT_FOUND 예외
    @Test
    void update_존재하지_않는_id_예외() {
        assertThatThrownBy(() -> categoryService.update(99999L, new CategoryUpdateRequest("신선식품", 1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    // 자식·상품이 없는 카테고리는 정상 삭제된다
    @Test
    void delete_정상_삭제_성공() {
        ProductCategory category = categoryRepository.save(ProductCategory.create(null, "식품", 1));

        categoryService.delete(category.getId());

        assertThat(categoryRepository.findById(category.getId())).isEmpty();
    }

    // 존재하지 않는 id로 삭제 시도 → CATEGORY_NOT_FOUND 예외
    @Test
    void delete_존재하지_않는_id_예외() {
        assertThatThrownBy(() -> categoryService.delete(99999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND));
    }

    // 자식 카테고리가 있는 경우 삭제 차단 → CATEGORY_HAS_CHILDREN
    @Test
    void delete_자식_카테고리_존재_시_예외() {
        ProductCategory root = categoryRepository.save(ProductCategory.create(null, "식품", 1));
        categoryRepository.save(ProductCategory.create(root.getId(), "과일", 1));

        assertThatThrownBy(() -> categoryService.delete(root.getId()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CATEGORY_HAS_CHILDREN));
    }
}
