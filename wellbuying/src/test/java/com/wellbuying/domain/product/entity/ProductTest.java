package com.wellbuying.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductTest {

    // register()로 생성한 상품은 항상 판매 가능 상태로 시작한다
    @Test
    void register_상품등록시_판매가능상태로_시작한다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");

        assertThat(product.isAvailable()).isTrue();
    }

    // markSoldOut() 호출 시 판매 불가 상태로 바뀐다
    @Test
    void markSoldOut_호출하면_판매불가_상태가_된다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");

        product.markSoldOut();

        assertThat(product.isAvailable()).isFalse();
    }

    // markAvailable() 호출 시 다시 판매 가능 상태로 돌아온다
    @Test
    void markAvailable_호출하면_판매가능_상태로_돌아온다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");
        product.markSoldOut();

        product.markAvailable();

        assertThat(product.isAvailable()).isTrue();
    }

    // 가격이 음수면 등록할 수 없다
    @Test
    void register_가격이_음수면_예외가_발생한다() {
        assertThatThrownBy(() -> Product.register(1L, 1L, "테스트 상품", "설명", -1000, "url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 상품명이 비어있으면 등록할 수 없다
    @Test
    void register_상품명이_비어있으면_예외가_발생한다() {
        assertThatThrownBy(() -> Product.register(1L, 1L, "   ", "설명", 10000, "url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 판매자 ID가 없으면 등록할 수 없다
    @Test
    void register_판매자ID가_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> Product.register(null, 1L, "테스트 상품", "설명", 10000, "url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}