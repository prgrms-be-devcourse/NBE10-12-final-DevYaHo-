package com.wellbuying.domain.groupbuy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record GroupBuyCreateRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @Positive int minQuantity,
        @Positive int maxQuantity,
        @NotEmpty @Valid List<PriceTierRequest> priceTiers
) {

    public record PriceTierRequest(
            @Positive int tierOrder,
            @Positive int thresholdQuantity,
            @Positive int unitPrice
    ) {
    }
}
