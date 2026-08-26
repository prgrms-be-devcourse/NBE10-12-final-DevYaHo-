package com.wellbuying.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void register_상품등록시_승인대기_상태로_시작한다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");

        assertThat(product.isApproved()).isFalse();
    }

    @Test
    void approve_대기중상태에서_호출하면_승인된다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");

        product.approve();

        assertThat(product.isApproved()).isTrue();
    }

    @Test
    void approve_대기중이_아니면_예외가_발생한다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");
        product.approve();

        assertThatThrownBy(product::approve)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_ALREADY_PROCESSED);
    }

    @Test
    void reject_대기중상태에서_호출하면_거절된다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");

        product.reject();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.REJECTED);
    }

    @Test
    void reject_대기중이_아니면_예외가_발생한다() {
        Product product = Product.register(1L, 1L, "테스트 상품", "설명", 10000, "url");
        product.reject();

        assertThatThrownBy(product::reject)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_ALREADY_PROCESSED);
    }

    @Test
    void register_가격이_음수면_예외가_발생한다() {
        assertThatThrownBy(() -> Product.register(1L, 1L, "테스트 상품", "설명", -1000, "url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_상품명이_비어있으면_예외가_발생한다() {
        assertThatThrownBy(() -> Product.register(1L, 1L, "   ", "설명", 10000, "url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void register_판매자ID가_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> Product.register(null, 1L, "테스트 상품", "설명", 10000, "url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
