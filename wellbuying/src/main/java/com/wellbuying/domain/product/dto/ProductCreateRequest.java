package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductCreateRequest(
        @NotNull Long categoryId,
        @NotBlank String productName,
        String description,
        @NotNull @PositiveOrZero Integer startPrice,
        String thumbnailUrl
) {
}