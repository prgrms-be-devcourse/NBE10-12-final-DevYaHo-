package com.wellbuying.domain.product.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.dto.ProductCreateRequest;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // 카테고리/가격 필터와 정렬 조건을 받아 상품 목록을 페이지 단위로 조회
    @GetMapping
    public Slice<ProductSummaryResponse> getProducts(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false, defaultValue = "LATEST") ProductSortType sort,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        ProductSearchCondition condition = new ProductSearchCondition(category, minPrice, maxPrice, sort);
        return productService.getProducts(condition, pageable);
    }

    // 생산자(SELLER)만 상품 등록 가능
    @PostMapping
    public ResponseEntity<Void> createProduct(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Long productId = productService.createProduct(authenticatedMember.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/products/" + productId)
                .build();
    }

    // 로그인한 판매자 본인이 등록한 상품 전체 조회
    @GetMapping("/mine")
    public Slice<ProductMineResponse> getMyProducts(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return productService.getMyProducts(authenticatedMember.memberId(), pageable);
    }
}