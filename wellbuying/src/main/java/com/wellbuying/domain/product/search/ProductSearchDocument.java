package com.wellbuying.domain.product.search;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "search_product_document")
@Setting(settingPath = "opensearch/product-analysis-settings.json")
public class ProductSearchDocument {

    @Id
    private final Long id;

    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    private final String productName;

    @Field(type = FieldType.Text, analyzer = "korean_analyzer")
    private final String description;

    @Field(type = FieldType.Long)
    private final Long categoryId;

    @Field(type = FieldType.Keyword)
    private final String status;

    @Field(type = FieldType.Integer)
    private final Integer startPrice;

    @Field(type = FieldType.Long)
    private final Long viewCount;

    @Field(type = FieldType.Keyword, index = false)
    private final String thumbnailUrl;

    @Field(type = FieldType.Long)
    private final Long sellerId;

    @Field(type = FieldType.Date)
    private final LocalDateTime createdAt;

    @PersistenceCreator
    public ProductSearchDocument(Long id, String productName, String description, Long categoryId,
                                 String status, Integer startPrice, Long viewCount,
                                 String thumbnailUrl, Long sellerId, LocalDateTime createdAt) {
        this.id = id;
        this.productName = productName;
        this.description = description;
        this.categoryId = categoryId;
        this.status = status;
        this.startPrice = startPrice;
        this.viewCount = viewCount;
        this.thumbnailUrl = thumbnailUrl;
        this.sellerId = sellerId;
        this.createdAt = createdAt;
    }

    public static ProductSearchDocument of(Long id, String productName, String description,
                                           Long categoryId, String status, Integer startPrice,
                                           Long viewCount, String thumbnailUrl, Long sellerId,
                                           LocalDateTime createdAt) {
        return new ProductSearchDocument(id, productName, description, categoryId, status,
                startPrice, viewCount, thumbnailUrl, sellerId, createdAt);
    }

    public Long getId() { return id; }
    public String getProductName() { return productName; }
    public String getDescription() { return description; }
    public Long getCategoryId() { return categoryId; }
    public String getStatus() { return status; }
    public Integer getStartPrice() { return startPrice; }
    public Long getViewCount() { return viewCount; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public Long getSellerId() { return sellerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
