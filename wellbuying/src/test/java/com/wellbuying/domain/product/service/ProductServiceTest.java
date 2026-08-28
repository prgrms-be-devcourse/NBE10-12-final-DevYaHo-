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
import com.wellbuying.domain.product.dto.ProductDetailResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.domain.product.search.ProductSearchDataChangedEvent;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

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
    private ApplicationEventPublisher eventPublisher;

    // getProducts 호출 시 전달받은 condition/pageable을 그대로 리포지토리에 넘기고, 결과를 그대로 반환한다
    @Test
    void getProducts_리포지토리_결과를_그대로_반환한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        ProductSearchCondition condition = new ProductSearchCondition(1L, 1000, 5000, ProductSortType.LATEST);
        PageRequest pageable = PageRequest.of(0, 20);
        ProductSummaryResponse response = new ProductSummaryResponse(1L, "상품", 3000, "url", 0L);
        Slice<ProductSummaryResponse> mockSlice = new SliceImpl<>(List.of(response), pageable, false);
        when(productRepository.search(condition, pageable)).thenReturn(mockSlice);

        Slice<ProductSummaryResponse> result = productService.getProducts(condition, pageable);

        assertThat(result.getContent()).containsExactly(response);
        verify(productRepository).search(condition, pageable);
    }

    // 존재하는 상품 ID로 조회하면 엔티티 필드를 그대로 담은 상세 응답을 반환한다
    @Test
    void getDetail_존재하는_상품이면_상세정보를_반환한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(10L);
        when(product.getProductName()).thenReturn("상품");
        when(product.getDescription()).thenReturn("설명");
        when(product.getStartPrice()).thenReturn(3000);
        when(product.getThumbnailUrl()).thenReturn("url");
        when(product.isApproved()).thenReturn(true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        ProductDetailResponse result = productService.getDetail(10L);

        assertThat(result).isEqualTo(new ProductDetailResponse(10L, "상품", "설명", 3000, "url", true));
    }

    // 존재하지 않는 상품 ID로 조회하면 PRODUCT_NOT_FOUND 예외를 던진다
    @Test
    void getDetail_존재하지_않으면_예외를_던진다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getDetail(99L)).isInstanceOf(BusinessException.class);
    }

    // 상품 등록 성공 시 검색 인덱스 동기화를 위한 이벤트가 발행된다
    @Test
    void createProduct_성공시_검색동기화_이벤트를_발행한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        Member seller = mock(Member.class);
        when(seller.getRole()).thenReturn(Role.SELLER);
        when(memberRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(seller));
        when(productCategoryRepository.existsById(10L)).thenReturn(true);
        Product savedProduct = mock(Product.class);
        when(savedProduct.getId()).thenReturn(42L);
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);
        ProductCreateRequest request = new ProductCreateRequest(10L, "상품명", "설명", 1000, "url");

        productService.createProduct(1L, request);

        ArgumentCaptor<ProductSearchDataChangedEvent> captor = ArgumentCaptor.forClass(ProductSearchDataChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().productId()).isEqualTo(42L);
    }

    // approve 호출 시 조회한 Product의 approve()를 위임 호출한다
    @Test
    void approve_PENDING_상품을_승인한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.approve(1L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.APPROVED);
    }

    // 존재하지 않는 productId로 approve 호출 시 PRODUCT_NOT_FOUND 예외를 던진다
    @Test
    void approve_존재하지_않는_상품이면_예외를_던진다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.approve(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_NOT_FOUND);
    }

    // reject 호출 시 조회한 Product의 reject()를 위임 호출한다
    @Test
    void reject_PENDING_상품을_거절한다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.reject(1L);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.REJECTED);
    }

    // 이미 처리된(APPROVED) 상품을 다시 승인 시도하면 PRODUCT_ALREADY_PROCESSED 예외를 던진다
    @Test
    void approve_이미_처리된_상품이면_예외를_던진다() {
        ProductService productService = new ProductService(productRepository, memberRepository, productCategoryRepository, eventPublisher);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        product.approve();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.approve(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_ALREADY_PROCESSED);
    }
}
