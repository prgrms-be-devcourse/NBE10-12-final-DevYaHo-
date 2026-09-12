package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
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
        List<String> descriptionImageUrls,
        // 진행 중(READY/ONGOING)인 공동구매가 있을 때만 채워진다 - 없으면 둘 다 null
        Long activeGroupBuyId,
        String activeGroupBuyStatus
) {

    public static ProductDetailResponse of(Product product, List<String> galleryImageUrls,
            List<String> descriptionImageUrls, GroupBuy activeGroupBuy) {
        return new ProductDetailResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getStartPrice(),
                product.getThumbnailUrl(),
                product.isApproved(),
                galleryImageUrls,
                descriptionImageUrls,
                activeGroupBuy != null ? activeGroupBuy.getId() : null,
                activeGroupBuy != null ? activeGroupBuy.getStatus().name() : null);
    }
}
