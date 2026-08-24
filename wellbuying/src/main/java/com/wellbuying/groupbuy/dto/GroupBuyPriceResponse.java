package com.wellbuying.groupbuy.dto;

import com.wellbuying.groupbuy.domain.GroupBuyPrice;

public record GroupBuyPriceResponse(
        int tierOrder,
        int thresholdQuantity,
        int unitPrice
) {

    public static GroupBuyPriceResponse of(GroupBuyPrice price) {
        return new GroupBuyPriceResponse(price.getTierOrder(), price.getThresholdQuantity(), price.getUnitPrice());
    }
}
