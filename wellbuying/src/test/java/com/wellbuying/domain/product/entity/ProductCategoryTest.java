package com.wellbuying.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductCategoryTest {

    // parentId가 null이면 최상위 카테고리로 생성된다
    @Test
    void create_parentId가_null이면_최상위_카테고리로_생성된다() {
        ProductCategory category = ProductCategory.create(null, "전자제품");

        assertThat(category.getParentId()).isNull();
    }

    // parentId가 있으면 하위 카테고리로 생성된다
    @Test
    void create_parentId가_있으면_하위_카테고리로_생성된다() {
        ProductCategory category = ProductCategory.create(1L, "노트북");

        assertThat(category.getParentId()).isEqualTo(1L);
    }

    // 카테고리명이 비어있으면 생성할 수 없다
    @Test
    void create_카테고리명이_비어있으면_예외가_발생한다() {
        assertThatThrownBy(() -> ProductCategory.create(null, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}