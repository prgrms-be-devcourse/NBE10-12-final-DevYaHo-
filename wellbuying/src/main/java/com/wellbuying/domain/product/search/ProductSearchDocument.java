package com.wellbuying.domain.product.search;

import com.wellbuying.domain.groupbuy.dto.GroupBuyProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "search_product_document")
@Setting(settingPath = "opensearch/product-analysis-settings.json")
public record ProductSearchDocument(
        @Id Long id,
        @Field(type = FieldType.Text, analyzer = "korean_analyzer") String productName,
        @Field(type = FieldType.Text, analyzer = "korean_analyzer") String description,
        @Field(type = FieldType.Long) Long categoryId,
        @Field(type = FieldType.Keyword) String status,
        @Field(type = FieldType.Integer) Integer startPrice,
        @Field(type = FieldType.Long) Long viewCount,
        @Field(type = FieldType.Keyword, index = false) String thumbnailUrl,
        @Field(type = FieldType.Long) Long sellerId,
        @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis) LocalDateTime createdAt,
        @Field(type = FieldType.Boolean) Boolean hasActiveGroupBuy,
        @Field(type = FieldType.Long) Long groupBuyId,
        @Field(type = FieldType.Text, analyzer = "korean_analyzer") String groupBuyTitle,
        @Field(type = FieldType.Keyword) String groupBuyStatus,
        @Field(type = FieldType.Integer) Integer currentUnitPrice,
        @Field(type = FieldType.Integer) Integer currentQuantity,
        @Field(type = FieldType.Integer) Integer targetQuantity,
        @Field(type = FieldType.Integer) Integer maxQuantity,
        @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis) LocalDateTime endAt
) {
    public static ProductSearchDocument of(Product product) {
        return of(product, null, 0L);
    }

    public static ProductSearchDocument of(Product product, GroupBuyProductSummaryResponse summary, Long viewCount) {
        boolean hasActive = summary != null;
        long resolvedViewCount = viewCount != null ? viewCount : 0L;
        return new ProductSearchDocument(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getCategoryId(),
                product.getStatus().name(),
                product.getStartPrice(),
                resolvedViewCount,
                product.getThumbnailUrl(),
                product.getSellerId(),
                product.getCreatedAt(),
                hasActive,
                hasActive ? summary.groupBuyId() : null,
                hasActive ? summary.title() : null,
                hasActive ? summary.groupBuyStatus().name() : null,
                hasActive ? summary.currentUnitPrice() : null,
                hasActive ? summary.currentQuantity() : null,
                hasActive ? summary.targetQuantity() : null,
                hasActive ? summary.maxQuantity() : null,
                hasActive ? summary.endAt() : null
        );
    }
}
