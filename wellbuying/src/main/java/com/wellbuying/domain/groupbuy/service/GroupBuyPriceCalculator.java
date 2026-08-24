package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPrice;
import java.util.Comparator;
import java.util.List;

public final class GroupBuyPriceCalculator {

    private GroupBuyPriceCalculator() {
    }

    // 누적 참여 수량이 도달한 가장 높은 구간의 단가를 반환 (아직 어떤 구간에도 도달하지 못했다면 가장 낮은 구간의 단가를 기본값으로 사용)
    public static int resolveUnitPrice(List<GroupBuyPrice> tiers, int cumulativeQuantity) {
        return tiers.stream()
                .filter(tier -> tier.getThresholdQuantity() <= cumulativeQuantity)
                .max(Comparator.comparingInt(GroupBuyPrice::getThresholdQuantity))
                .or(() -> tiers.stream().min(Comparator.comparingInt(GroupBuyPrice::getThresholdQuantity)))
                .map(GroupBuyPrice::getUnitPrice)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_PRICE_TIER_NOT_FOUND));
    }
}
