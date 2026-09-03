package com.wellbuying.domain.product.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductCountTest {

    // init()으로 생성하면 모든 카운트가 0으로 시작한다
    @Test
    void init_생성시_모든_카운트가_0으로_시작한다() {
        ProductCount count = ProductCount.init(1L);

        assertThat(count.getViewCount()).isZero();
        assertThat(count.getLikeCount()).isZero();
        assertThat(count.getGroupbuyParticipantCount()).isZero();
    }

    // increaseViewCount() 호출 시 조회수가 1 증가한다
    @Test
    void increaseViewCount_호출하면_조회수가_1_증가한다() {
        ProductCount count = ProductCount.init(1L);

        count.increaseViewCount();

        assertThat(count.getViewCount()).isEqualTo(1L);
    }
}