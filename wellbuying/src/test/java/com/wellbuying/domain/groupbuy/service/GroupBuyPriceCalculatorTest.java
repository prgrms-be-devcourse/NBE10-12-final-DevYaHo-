package com.wellbuying.domain.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import java.util.List;

import org.junit.jupiter.api.Test;

class GroupBuyPriceCalculatorTest {

    private final GroupBuyPrice tier1 = GroupBuyPrice.of(1L, 1, 100, 15_000);
    private final GroupBuyPrice tier2 = GroupBuyPrice.of(1L, 2, 1_000, 12_000);
    private final GroupBuyPrice tier3 = GroupBuyPrice.of(1L, 3, 10_000, 10_000);

    // 누적 수량이 아직 첫 구간 기준에도 못 미치면 가장 낮은 구간(기본가)을 적용한다
    @Test
    void 첫_구간_미만이면_기본_구간_단가를_적용한다() {
        int unitPrice = GroupBuyPriceCalculator.resolveUnitPrice(List.of(tier1, tier2, tier3), 50);

        assertThat(unitPrice).isEqualTo(15_000);
    }

    // 누적 수량이 특정 구간의 기준 수량 이상이면 해당 구간 단가를 적용한다
    @Test
    void 기준_수량에_도달하면_해당_구간_단가를_적용한다() {
        int unitPrice = GroupBuyPriceCalculator.resolveUnitPrice(List.of(tier1, tier2, tier3), 1_500);

        assertThat(unitPrice).isEqualTo(12_000);
    }

    // 구간 목록 순서가 뒤섞여 있어도 threshold 기준으로 정렬해 가장 높은 도달 구간을 찾는다
    @Test
    void 구간_순서가_뒤섞여도_올바른_구간을_찾는다() {
        int unitPrice = GroupBuyPriceCalculator.resolveUnitPrice(List.of(tier3, tier1, tier2), 10_000);

        assertThat(unitPrice).isEqualTo(10_000);
    }
}
