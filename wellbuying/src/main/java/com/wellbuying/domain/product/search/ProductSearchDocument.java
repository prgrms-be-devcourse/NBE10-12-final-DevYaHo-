package com.wellbuying.domain.product.search;

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
        @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis) LocalDateTime createdAt
) {
    public static ProductSearchDocument of(Product product) {
        return new ProductSearchDocument(
                product.getId(),
                product.getProductName(),
                product.getDescription(),
                product.getCategoryId(),
                product.getStatus().name(),
                product.getStartPrice(),
                0L,
                product.getThumbnailUrl(),
                product.getSellerId(),
                product.getCreatedAt()
        );
    }
}
