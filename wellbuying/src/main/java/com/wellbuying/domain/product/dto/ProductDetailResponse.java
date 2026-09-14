package com.wellbuying.domain.product.dto;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.product.entity.Product;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ProductDetailResponse(
        Long id,
        String productName,
        String description,
        Integer startPrice,
        String thumbnailUrl,
        boolean approved,
        List<String> galleryImageUrls,
        List<String> descriptionImageUrls,
        // 진행 중(READY/ONGOING)인 공동구매 전체 - 없으면 빈 리스트. 생성 시 동일 상품에 여러 건이 열리는
        // 것을 막는 검증이 없어 한 상품에 여러 건이 동시에 활성 상태일 수 있다
        List<ActiveGroupBuySummary> activeGroupBuys
) {

    public static ProductDetailResponse of(Product product, List<String> galleryImageUrls,
            List<String> descriptionImageUrls, List<GroupBuy> activeGroupBuys,
            Map<Long, Integer> currentUnitPriceByGroupBuyId) {
        return new ProductDetailResponse(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getStartPrice(),
                product.getThumbnailUrl(),
                product.isApproved(),
                galleryImageUrls,
                descriptionImageUrls,
                activeGroupBuys.stream()
                        .map(groupBuy -> ActiveGroupBuySummary.of(groupBuy,
                                currentUnitPriceByGroupBuyId.get(groupBuy.getId())))
                        .toList());
    }

    // currentUnitPrice - 해당 공동구매의 가격 구간 중 현재 누적 참여 수량이 도달한 구간의 단가
    // (GroupBuyPriceCalculator.resolveUnitPrice). Product.startPrice(판매자가 등록한 고정 기준가)와 달리
    // 참여자 수에 따라 바뀐다
    public record ActiveGroupBuySummary(Long id, String title, String status, LocalDateTime endAt,
            Integer currentUnitPrice) {
        public static ActiveGroupBuySummary of(GroupBuy groupBuy, Integer currentUnitPrice) {
            return new ActiveGroupBuySummary(groupBuy.getId(), groupBuy.getTitle(), groupBuy.getStatus().name(),
                    groupBuy.getEndAt(), currentUnitPrice);
        }
    }
}
