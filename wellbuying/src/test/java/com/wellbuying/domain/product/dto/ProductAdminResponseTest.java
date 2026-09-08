package com.wellbuying.domain.product.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductAdminResponseTest {

    @Test
    void of_Product_필드를_그대로_매핑한다() {
        Product product = mock(Product.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 5, 10, 0);
        when(product.getId()).thenReturn(1L);
        when(product.getSellerId()).thenReturn(10L);
        when(product.getCategoryId()).thenReturn(100L);
        when(product.getProductName()).thenReturn("상품명");
        when(product.getStartPrice()).thenReturn(5000);
        when(product.getThumbnailUrl()).thenReturn("thumb.jpg");
        when(product.getStatus()).thenReturn(ProductStatus.PENDING);
        when(product.getCreatedAt()).thenReturn(createdAt);

        ProductAdminResponse response = ProductAdminResponse.of(product);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sellerId()).isEqualTo(10L);
        assertThat(response.categoryId()).isEqualTo(100L);
        assertThat(response.productName()).isEqualTo("상품명");
        assertThat(response.startPrice()).isEqualTo(5000);
        assertThat(response.thumbnailUrl()).isEqualTo("thumb.jpg");
        assertThat(response.status()).isEqualTo(ProductStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
