package com.wellbuying.domain.product.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.dto.GroupBuyProductSummaryResponse;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductSearchDocumentTest {

    private Product mockProduct() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(1L);
        when(product.getProductName()).thenReturn("상품");
        when(product.getDescription()).thenReturn("설명");
        when(product.getCategoryId()).thenReturn(1L);
        when(product.getStatus()).thenReturn(ProductStatus.APPROVED);
        when(product.getStartPrice()).thenReturn(10000);
        when(product.getThumbnailUrl()).thenReturn("url");
        when(product.getSellerId()).thenReturn(1L);
        return product;
    }

    @Test
    void of_summary가_null이면_hasActiveGroupBuy가_false이고_공동구매_필드는_null이다() {
        ProductSearchDocument doc = ProductSearchDocument.of(mockProduct(), null, 0L);

        assertThat(doc.hasActiveGroupBuy()).isFalse();
        assertThat(doc.groupBuyStatus()).isNull();
        assertThat(doc.currentUnitPrice()).isNull();
        assertThat(doc.currentQuantity()).isNull();
        assertThat(doc.targetQuantity()).isNull();
    }

    @Test
    void of_summary가_있으면_hasActiveGroupBuy가_true이고_요약_값이_매핑된다() {
        GroupBuyProductSummaryResponse summary = new GroupBuyProductSummaryResponse(100L, "감귤 공동구매",
                GroupBuyStatus.ONGOING, 8000, 5, 10, 100, LocalDateTime.of(2026, 9, 30, 23, 59));

        ProductSearchDocument doc = ProductSearchDocument.of(mockProduct(), summary, 0L);

        assertThat(doc.hasActiveGroupBuy()).isTrue();
        assertThat(doc.groupBuyStatus()).isEqualTo("ONGOING");
        assertThat(doc.currentUnitPrice()).isEqualTo(8000);
        assertThat(doc.currentQuantity()).isEqualTo(5);
        assertThat(doc.targetQuantity()).isEqualTo(10);
        assertThat(doc.groupBuyId()).isEqualTo(100L);
        assertThat(doc.groupBuyTitle()).isEqualTo("감귤 공동구매");
        assertThat(doc.maxQuantity()).isEqualTo(100);
        assertThat(doc.endAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 23, 59));
    }

    @Test
    void of_Product만_받는_팩토리는_summary_null로_위임한다() {
        ProductSearchDocument doc = ProductSearchDocument.of(mockProduct());

        assertThat(doc.hasActiveGroupBuy()).isFalse();
        assertThat(doc.groupBuyStatus()).isNull();
    }
}
