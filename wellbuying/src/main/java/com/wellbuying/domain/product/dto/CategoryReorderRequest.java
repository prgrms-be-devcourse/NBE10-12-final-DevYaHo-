package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotNull;

public record CategoryReorderRequest(
        @NotNull(message = "카테고리 ID는 필수입니다")
        Long id,
        
        @NotNull(message = "노출 순서는 필수입니다")
        Integer sortOrder
) {
}
