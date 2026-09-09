package com.wellbuying.domain.product.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.product.entity.ProductSortType;
import com.wellbuying.domain.product.dto.ProductCreateRequest;
import com.wellbuying.domain.product.dto.ProductDeleteRequest;
import com.wellbuying.domain.product.dto.ProductDescriptionImageUploadUrlResponse;
import com.wellbuying.domain.product.dto.ProductGalleryImageUploadUrlResponse;
import com.wellbuying.domain.product.dto.ProductImageUploadUrlRequest;
import com.wellbuying.domain.product.dto.ProductImageUploadUrlResponse;
import com.wellbuying.domain.product.dto.ProductImageSaveRequest;
import com.wellbuying.domain.product.dto.ProductUpdateRequest;
import com.wellbuying.domain.product.dto.ProductDetailResponse;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.ImageType;
import com.wellbuying.domain.product.search.ProductAutocompleteResponse;
import com.wellbuying.domain.product.search.ProductSearchRequest;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.service.ProductImageService;
import com.wellbuying.domain.product.service.ProductImageUploadService;
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
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final ProductImageUploadService productImageUploadService;
    private final ProductImageService productImageService;

    public ProductController(ProductService productService, ProductSearchService productSearchService,
            ProductImageUploadService productImageUploadService, ProductImageService productImageService) {
        this.productService = productService;
        this.productSearchService = productSearchService;
        this.productImageUploadService = productImageUploadService;
        this.productImageService = productImageService;
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

    // 메인 페이지 홈 - 조회수 기준 인기 상품 TOP 10 (캐시 적용)
    @Operation(summary = "인기 상품 TOP 10 조회 - 조회수 기준, 캐시 적용")
    @GetMapping("/popular")
    public List<ProductSummaryResponse> getPopularProducts() {
        return productService.getPopularProducts();
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

    // 판매자가 상품 썸네일 이미지를 업로드할 presigned URL 발급
    @Operation(summary = "상품 썸네일 업로드 URL 발급 - 판매자 전용")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/thumbnail/upload-url")
    public ResponseEntity<ProductImageUploadUrlResponse> issueThumbnailUploadUrl(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ProductImageUploadUrlRequest request) {
        ProductImageUploadUrlResponse response = productImageUploadService.issueUploadUrl(
                authenticatedMember.memberId(), request);
        return ResponseEntity.ok(response);
    }

    // 판매자가 상품 갤러리 이미지를 업로드할 presigned URL 발급 - 등록 전/후 모두 호출 가능
    @Operation(summary = "상품 갤러리 이미지 업로드 URL 발급 - 판매자 전용")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/gallery-images/upload-url")
    public ResponseEntity<ProductGalleryImageUploadUrlResponse> issueGalleryImageUploadUrl(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ProductImageUploadUrlRequest request) {
        ProductGalleryImageUploadUrlResponse response = productImageUploadService.issueGalleryImageUploadUrl(
                authenticatedMember.memberId(), request);
        return ResponseEntity.ok(response);
    }

    // 판매자가 상품 상세설명 이미지를 업로드할 presigned URL 발급 - 등록 전/후 모두 호출 가능
    @Operation(summary = "상품 상세설명 이미지 업로드 URL 발급 - 판매자 전용")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/description-images/upload-url")
    public ResponseEntity<ProductDescriptionImageUploadUrlResponse> issueDescriptionImageUploadUrl(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Valid @RequestBody ProductImageUploadUrlRequest request) {
        ProductDescriptionImageUploadUrlResponse response = productImageUploadService.issueDescriptionImageUploadUrl(
                authenticatedMember.memberId(), request);
        return ResponseEntity.ok(response);
    }

    // 판매자가 상품 갤러리 이미지 목록을 저장(전체 교체) - 새로 추가된 이미지는 확정, 빠진 이미지는 정리
    @Operation(summary = "상품 갤러리 이미지 저장 - 판매자 전용, 최대 6장")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PutMapping("/{id}/gallery-images")
    public ResponseEntity<Void> saveGalleryImages(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long id,
            @Valid @RequestBody ProductImageSaveRequest request) {
        productImageService.saveImages(authenticatedMember.memberId(), id, ImageType.GALLERY,
                request.imageUrls());
        return ResponseEntity.noContent().build();
    }

    // 판매자가 상품 상세설명 이미지 목록을 저장(전체 교체) - 새로 추가된 이미지는 확정, 빠진 이미지는 정리
    @Operation(summary = "상품 상세설명 이미지 저장 - 판매자 전용, 최대 10장")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PutMapping("/{id}/description-images")
    public ResponseEntity<Void> saveDescriptionImages(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long id,
            @Valid @RequestBody ProductImageSaveRequest request) {
        productImageService.saveImages(authenticatedMember.memberId(), id, ImageType.DESCRIPTION,
                request.imageUrls());
        return ResponseEntity.noContent().build();
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

    // 판매자 본인이 등록한 상품 수정
    @Operation(summary = "상품 수정 - 판매자 전용")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateProduct(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        productService.updateProduct(authenticatedMember.memberId(), id, request);
        return ResponseEntity.noContent().build();
    }

    // 판매자 본인이 등록한 상품 소프트 삭제 - 사유 필수
    // DELETE + RequestBody는 일부 프록시/클라이언트에서 바디가 유실될 수 있어 POST로 처리
    // (관리자 강제 삭제 API와 동일한 이유)
    @Operation(summary = "상품 삭제 - 판매자 전용, 사유 필수")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long id,
            @Valid @RequestBody ProductDeleteRequest request
    ) {
        productService.deleteProduct(authenticatedMember.memberId(), id, request.reason());
        return ResponseEntity.noContent().build();
    }

    // 키워드로 승인된 상품 전문 검색 (OpenSearch), 기본 정렬은 관련도순(_score)
    @GetMapping("/search")
    public CursorPageResponse<ProductSearchResponse> searchProducts(@Valid @ModelAttribute ProductSearchRequest request) {
        return productSearchService.search(request.keyword(), request.sort(), request.cursor(), request.size(), request.toFilter());
    }

    // 검색창 자동완성 - 상품명 접두어 일치, 최대 8건, 필터/페이지네이션 없음
    @GetMapping("/search/autocomplete")
    public List<ProductAutocompleteResponse> autocomplete(
            @RequestParam @NotBlank(message = "검색 키워드는 필수입니다.") String keyword) {
        return productSearchService.autocomplete(keyword);
    }
}
