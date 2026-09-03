package com.wellbuying.domain.product.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.domain.product.dto.CategoryTreeResponse;
import com.wellbuying.domain.product.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    // 카테고리 트리가 JSON 배열로 정상 응답된다
    @Test
    void getCategories_트리구조가_정상응답된다() throws Exception {
        CategoryTreeResponse child = new CategoryTreeResponse(2L, "노트북", List.of());
        CategoryTreeResponse root = new CategoryTreeResponse(1L, "전자제품", List.of(child));
        when(categoryService.getCategoryTree()).thenReturn(List.of(root));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("전자제품"))
                .andExpect(jsonPath("$[0].children[0].categoryName").value("노트북"));
    }
}