package com.wellbuying.domain.product.dto;

import java.util.List;

public record CategoryTreeResponse(
        Long id,
        String categoryName,
        List<CategoryTreeResponse> children
) {
}