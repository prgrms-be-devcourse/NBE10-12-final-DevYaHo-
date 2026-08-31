package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.product.service.ProductService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 상품 승인/거절 API - ADMIN role만 접근 가능
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "관리자 - 상품", description = "상품 승인/거절")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    // 상품 승인 - PRODUCT.status를 APPROVED로 변경
    @Operation(summary = "상품 승인 - PRODUCT.status를 APPROVED로 변경")
    @PostMapping("/{productId}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long productId) {
        productService.approve(productId);
        return ResponseEntity.noContent().build();
    }

    // 상품 거절 - PRODUCT.status를 REJECTED로 변경
    @Operation(summary = "상품 거절 - PRODUCT.status를 REJECTED로 변경")
    @PostMapping("/{productId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long productId) {
        productService.reject(productId);
        return ResponseEntity.noContent().build();
    }
}
