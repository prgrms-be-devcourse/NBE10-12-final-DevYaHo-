package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminProductDeleteRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
