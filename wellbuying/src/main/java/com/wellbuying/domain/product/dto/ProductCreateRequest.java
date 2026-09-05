package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 100) String productName,
        String description,
        @NotNull @PositiveOrZero Integer startPrice,
        String thumbnailUrl
) {
}
