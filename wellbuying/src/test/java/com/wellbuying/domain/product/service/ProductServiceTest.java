package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.dto.ProductCreateRequest;
import com.wellbuying.domain.product.dto.ProductUpdateRequest;
import com.wellbuying.domain.product.dto.ProductDetailResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductCountRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.domain.product.search.ProductSearchEventOutbox;
import com.wellbuying.domain.product.search.ProductSearchEventOutboxRepository;
import com.wellbuying.global.dto.CursorPageResponse;
import org.mockito.ArgumentCaptor;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.never;

// DB 없이 리포지토리를 가짜(Mock)로 대체해서, ProductService가 조건을 그대로 리포지토리에 위임하는지만 검증
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private ProductCountRepository productCountRepository;

    @Mock
    private ProductSearchEventOutboxRepository outboxRepository;

    // getProducts 호출 시 전달받은 condition/cursor/size를 그대로 리포지토리에 넘기고, 결과를 그대로 반환한다
    @Test
    void getProducts_리포지토리_결과를_그대로_반환한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        ProductSearchCondition condition = new ProductSearchCondition(1L, 1000, 5000, ProductSortType.LATEST);
        ProductSummaryResponse response = new ProductSummaryResponse(1L, "상품", 3000, "url", 0L);
        CursorPageResponse<ProductSummaryResponse> mockPage = new CursorPageResponse<>(List.of(response), null, false);
        when(productRepository.search(condition, null, 20)).thenReturn(mockPage);

        CursorPageResponse<ProductSummaryResponse> result = productService.getProducts(condition, null, 20);

        assertThat(result.content()).containsExactly(response);
        verify(productRepository).search(condition, null, 20);
    }

    // 존재하는 상품 ID로 조회하면 엔티티 필드를 그대로 담은 상세 응답을 반환한다
    @Test
    void getDetail_존재하는_상품이면_상세정보를_반환한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(10L);
        when(product.getProductName()).thenReturn("상품");
        when(product.getDescription()).thenReturn("설명");
        when(product.getStartPrice()).thenReturn(3000);
        when(product.getThumbnailUrl()).thenReturn("url");
        when(product.isApproved()).thenReturn(true);
        when(productRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(product));

        ProductDetailResponse result = productService.getDetail(10L);

        assertThat(result).isEqualTo(new ProductDetailResponse(10L, "상품", "설명", 3000, "url", true));
    }

    // 존재하지 않는 상품 ID로 조회하면 PRODUCT_NOT_FOUND 예외를 던진다
    @Test
    void getDetail_존재하지_않으면_예외를_던진다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        when(productRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getDetail(99L)).isInstanceOf(BusinessException.class);
    }

    // PENDING 상태는 검색 노출 대상이 아니므로 상품 등록 시 outbox에 기록하지 않는다
    @Test
    void createProduct_성공시_outbox를_기록하지_않는다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Member seller = mock(Member.class);
        when(seller.getRole()).thenReturn(Role.SELLER);
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        when(productCategoryRepository.existsById(10L)).thenReturn(true);
        Product savedProduct = mock(Product.class);
        when(savedProduct.getId()).thenReturn(42L);
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        ProductCreateRequest request = new ProductCreateRequest(10L, "상품명", "설명", 1000, "url");

        productService.createProduct(1L, request);

        verify(outboxRepository, never()).save(any());
    }

    // approve 호출 시 조회한 Product의 approve()를 위임 호출한다
    @Test
    void approve_PENDING_상품을_승인한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));

        productService.approve(1L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.APPROVED);
        ArgumentCaptor<ProductSearchEventOutbox> captor = ArgumentCaptor.forClass(ProductSearchEventOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(1L);
        assertThat(captor.getValue().getEventType()).isEqualTo("UPSERT");
    }

    // 존재하지 않는 productId로 approve 호출 시 PRODUCT_NOT_FOUND 예외를 던진다
    @Test
    void approve_존재하지_않는_상품이면_예외를_던진다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.approve(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    // reject 호출 시 조회한 Product의 reject()를 위임 호출한다
    @Test
    void reject_PENDING_상품을_거절한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));

        productService.reject(1L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.REJECTED);
        verify(outboxRepository, never()).save(any());
    }

    // 이미 처리된(APPROVED) 상품을 다시 승인 시도하면 PRODUCT_ALREADY_PROCESSED 예외를 던진다
    @Test
    void approve_이미_처리된_상품이면_예외를_던진다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        product.approve();
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.approve(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_ALREADY_PROCESSED);
    }

    @Test
    void updateProduct_PENDING_상품이면_outbox를_기록하지_않는다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = Product.register(1L, 10L, "상품", "설명", 10000, "url");
        when(productCategoryRepository.existsById(10L)).thenReturn(true);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        ProductUpdateRequest request = new ProductUpdateRequest(10L, "수정된상품", "수정설명", 9000, "new-url");

        productService.updateProduct(1L, 1L, request);

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void updateProduct_APPROVED_상품이면_outbox에_UPSERT를_기록한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = Product.register(1L, 10L, "상품", "설명", 10000, "url");
        product.approve();
        when(productCategoryRepository.existsById(10L)).thenReturn(true);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));
        ProductUpdateRequest request = new ProductUpdateRequest(10L, "수정된상품", "수정설명", 9000, "new-url");

        productService.updateProduct(1L, 1L, request);

        ArgumentCaptor<ProductSearchEventOutbox> captor = ArgumentCaptor.forClass(ProductSearchEventOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(1L);
        assertThat(captor.getValue().getEventType()).isEqualTo("UPSERT");
    }

    @Test
    void deleteProduct_PENDING_상품이면_outbox를_기록하지_않는다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = Product.register(1L, 10L, "상품", "설명", 10000, "url");
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L, 1L);

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void deleteProduct_APPROVED_상품이면_outbox에_DELETE를_기록한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        Product product = Product.register(1L, 10L, "상품", "설명", 10000, "url");
        product.approve();
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(product));

        productService.deleteProduct(1L, 1L);

        ArgumentCaptor<ProductSearchEventOutbox> captor = ArgumentCaptor.forClass(ProductSearchEventOutbox.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(1L);
        assertThat(captor.getValue().getEventType()).isEqualTo("DELETE");
    }

    @Test
    void deleteProduct_상품이_존재하지_않거나_이미_삭제된_경우_예외를_던진다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, productCountRepository, outboxRepository);
        when(productRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }
}
