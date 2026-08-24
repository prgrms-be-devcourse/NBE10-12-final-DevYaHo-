package com.wellbuying.groupbuy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroupBuyPartTest {

    // confirm()으로 만든 참여는 CONFIRMED 상태이고, 가격은 아직 성사 전이라 null인지 검증
    @Test
    void confirm으로_생성한_참여는_CONFIRMED_상태이고_가격은_아직_null이다() {
        GroupBuyPart part = GroupBuyPart.confirm(1L, 100L, 50);

        assertThat(part.getGroupBuyId()).isEqualTo(1L);
        assertThat(part.getMemberId()).isEqualTo(100L);
        assertThat(part.getQuantity()).isEqualTo(50);
        assertThat(part.getAppliedPrice()).isNull();
        assertThat(part.getStatus()).isEqualTo(GroupBuyPartStatus.CONFIRMED);
    }

    // cancel() 호출 시 CANCELED로 전환되는지 검증
    @Test
    void cancel을_호출하면_CANCELED로_전환된다() {
        GroupBuyPart part = GroupBuyPart.confirm(1L, 100L, 50);

        part.cancel();

        assertThat(part.getStatus()).isEqualTo(GroupBuyPartStatus.CANCELED);
    }

    // applyFinalPrice() 호출 전까지 null이던 가격이, 호출 시 전달받은 최종가로 채워지는지 검증
    @Test
    void applyFinalPrice를_호출하면_null이던_가격이_최종가로_채워진다() {
        GroupBuyPart part = GroupBuyPart.confirm(1L, 100L, 50);

        part.applyFinalPrice(10_000);

        assertThat(part.getAppliedPrice()).isEqualTo(10_000);
    }
}
