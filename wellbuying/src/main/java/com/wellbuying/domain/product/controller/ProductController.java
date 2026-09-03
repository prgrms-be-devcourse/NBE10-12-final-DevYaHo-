package com.wellbuying.domain.product.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.dto.ProductCreateRequest;
import com.wellbuying.domain.product.dto.ProductDetailResponse;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.search.ProductSearchRequest;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.service.ProductSearchService;
import com.wellbuying.domain.product.service.ProductService;
import com.wellbuying.global.config.OpenApiConfig;
import com.wellbuying.global.dto.CursorPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/products")
@Tag(name = "상품", description = "상품 조회/등록")
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;

    public ProductController(ProductService productService, ProductSearchService productSearchService) {
        this.productService = productService;
        this.productSearchService = productSearchService;
    }

    // 카테고리/가격 필터와 정렬 조건을 받아 상품 목록을 커서 기반으로 조회
    @Operation(summary = "상품 목록 조회 - 카테고리/가격 필터, 정렬")
    @GetMapping
    public CursorPageResponse<ProductSummaryResponse> getProducts(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false, defaultValue = "LATEST") ProductSortType sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        ProductSearchCondition condition = new ProductSearchCondition(category, minPrice, maxPrice, sort);
        return productService.getProducts(condition, cursor, size);
    }

    // 상품 상세 - 설명/썸네일 등 목록에 없는 정보까지 포함해 단건 조회
    @Operation(summary = "상품 상세 조회")
    @GetMapping("/{id}")
    public ProductDetailResponse getProduct(@PathVariable Long id) {
        return productService.getDetail(id);
    }

    // 생산자(SELLER)만 상품 등록 가능
    @Operation(summary = "상품 등록 - 생산자(SELLER) 전용")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping
    public ResponseEntity<Void> createProduct(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Long productId = productService.createProduct(authenticatedMember.memberId(), request);
        return ResponseEntity.created(URI.create("/api/products/" + productId)).build();
    }

    // 로그인한 판매자 본인이 등록한 상품 전체 조회
    @Operation(summary = "내 상품 목록 조회")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @GetMapping("/mine")
    public Slice<ProductMineResponse> getMyProducts(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return productService.getMyProducts(authenticatedMember.memberId(), pageable);
    }

    // 키워드로 승인된 상품 전문 검색 (OpenSearch), 기본 정렬은 관련도순(_score)
    @GetMapping("/search")
    public CursorPageResponse<ProductSearchResponse> searchProducts(@Valid @ModelAttribute ProductSearchRequest request) {
        return productSearchService.search(request.keyword(), request.sort(), request.cursor(), request.size());
    }
}
