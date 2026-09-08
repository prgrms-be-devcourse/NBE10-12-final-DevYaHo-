package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProductImageSaveRequest(@NotNull List<String> imageUrls) {
}
