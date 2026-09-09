package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductImageUploadUrlRequest(@NotBlank String contentType) {
}
