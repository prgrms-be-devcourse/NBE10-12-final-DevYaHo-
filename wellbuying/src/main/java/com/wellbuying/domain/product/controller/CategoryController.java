package com.wellbuying.domain.product.controller;

import com.wellbuying.domain.product.dto.CategoryTreeResponse;
import com.wellbuying.domain.product.service.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // 전체 카테고리를 계층형 트리 구조로 조회
    @GetMapping
    public List<CategoryTreeResponse> getCategories() {
        return categoryService.getCategoryTree();
    }
}