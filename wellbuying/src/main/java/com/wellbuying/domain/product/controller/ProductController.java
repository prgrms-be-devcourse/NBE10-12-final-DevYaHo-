package com.wellbuying.domain.product.controller;

import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
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
}