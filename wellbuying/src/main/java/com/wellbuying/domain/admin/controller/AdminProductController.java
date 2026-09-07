package com.wellbuying.domain.admin.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.product.dto.AdminProductDeleteRequest;
import com.wellbuying.domain.product.dto.ProductAdminResponse;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.service.ProductService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    // 상태별 상품 심사 목록 조회 (예: ?status=PENDING으로 승인 대기 목록 조회)
    @GetMapping
    public ResponseEntity<Page<ProductAdminResponse>> list(@RequestParam ProductStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.findByStatus(status, pageable));
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

    // DELETE + RequestBody는 일부 프록시/클라이언트에서 바디가 유실될 수 있어 POST로 처리
    @Operation(summary = "상품 강제 삭제 - 소유권 무관, 사유 필수")
    @PostMapping("/{productId}/force-delete")
    public ResponseEntity<Void> forceDelete(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long productId,
            @Valid @RequestBody AdminProductDeleteRequest request
    ) {
        productService.adminDeleteProduct(authenticatedMember.memberId(), productId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
