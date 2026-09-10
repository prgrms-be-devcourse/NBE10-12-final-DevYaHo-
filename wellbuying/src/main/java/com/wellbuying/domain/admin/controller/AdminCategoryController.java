package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.product.dto.CategoryCreateRequest;
import com.wellbuying.domain.product.dto.CategoryResponse;
import com.wellbuying.domain.product.dto.CategoryUpdateRequest;
import com.wellbuying.domain.product.dto.CategoryReorderRequest;
import com.wellbuying.domain.product.service.CategoryService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "관리자 - 카테고리", description = "카테고리 생성/수정/삭제/순서변경 (ADMIN 전용)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "카테고리 생성 - parentId 없으면 최상위, 있으면 하위 카테고리 (최대 2뎁스)")
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @Operation(summary = "카테고리 이름 및 순서 단건 수정")
    @PatchMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ResponseEntity.ok(categoryService.update(categoryId, request));
    }

    @Operation(summary = "카테고리 순서 일괄 변경")
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@Valid @RequestBody List<CategoryReorderRequest> requests) {
        categoryService.reorder(requests);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "카테고리 삭제 - 하위 카테고리 또는 참조 상품이 있으면 차단")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable Long categoryId) {
        categoryService.delete(categoryId);
        return ResponseEntity.noContent().build();
    }
}
