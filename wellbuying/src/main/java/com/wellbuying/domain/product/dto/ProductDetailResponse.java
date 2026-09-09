package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.product.entity.Product;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String productName,
        String description,
        Integer startPrice,
        String thumbnailUrl,
        boolean approved,
        List<String> galleryImageUrls,
        List<String> descriptionImageUrls
) {

    public static ProductDetailResponse of(Product product, List<String> galleryImageUrls,
            List<String> descriptionImageUrls) {
        return new ProductDetailResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getStartPrice(),
                product.getThumbnailUrl(),
                product.isApproved(),
                galleryImageUrls,
                descriptionImageUrls);
    }
}
