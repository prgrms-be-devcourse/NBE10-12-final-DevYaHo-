package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoryCreateRequest(
        Long parentId,

        @NotBlank(message = "카테고리명은 필수입니다")
        String categoryName,

        @NotNull(message = "노출 순서는 필수입니다")
        Integer sortOrder
) {
}
