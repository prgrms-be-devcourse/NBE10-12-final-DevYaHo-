package com.wellbuying.groupbuy.dto;

import jakarta.validation.constraints.Positive;

public record GroupBuyPartCreateRequest(
        @Positive int quantity
) {
}
