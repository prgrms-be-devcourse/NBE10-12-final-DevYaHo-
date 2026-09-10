package com.wellbuying.domain.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.domain.product.dto.CategoryResponse;
import com.wellbuying.domain.product.service.CategoryService;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void create_최상위_카테고리_생성_성공_201() throws Exception {
        when(categoryService.create(any())).thenReturn(new CategoryResponse(1L, null, "식품", 1));

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"categoryName\":\"식품\",\"sortOrder\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.categoryName").value("식품"))
                .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    void create_하위_카테고리_생성_성공_201() throws Exception {
        when(categoryService.create(any())).thenReturn(new CategoryResponse(2L, 1L, "과일", 2));

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":1,\"categoryName\":\"과일\",\"sortOrder\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(1L))
                .andExpect(jsonPath("$.categoryName").value("과일"));
    }

    @Test
    void create_카테고리명_공백_400() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"categoryName\":\"  \",\"sortOrder\":1}"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void create_노출순서_누락_400() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":null,\"categoryName\":\"식품\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_3뎁스_시도_400() throws Exception {
        when(categoryService.create(any())).thenThrow(new BusinessException(ErrorCode.CATEGORY_DEPTH_EXCEEDED));

        mockMvc.perform(post("/api/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":2,\"categoryName\":\"사과\",\"sortOrder\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_400_DEPTH_EXCEEDED"));
    }

    @Test
    void update_카테고리명_변경_성공_200() throws Exception {
        when(categoryService.update(eq(1L), any())).thenReturn(new CategoryResponse(1L, null, "신선식품", 1));

        mockMvc.perform(patch("/api/admin/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"신선식품\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("신선식품"))
                .andExpect(jsonPath("$.sortOrder").value(1));
    }

    @Test
    void update_존재하지_않는_카테고리_404() throws Exception {
        when(categoryService.update(eq(99999L), any()))
                .thenThrow(new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(patch("/api/admin/categories/99999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryName\":\"신선식품\",\"sortOrder\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_404_CATEGORY_NOT_FOUND"));
    }

    @Test
    void delete_정상_삭제_204() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/1"))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(1L);
    }

    @Test
    void delete_자식_카테고리_존재_400() throws Exception {
        doThrow(new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN))
                .when(categoryService).delete(1L);

        mockMvc.perform(delete("/api/admin/categories/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_400_HAS_CHILDREN"));
    }

    @Test
    void delete_참조_상품_존재_400() throws Exception {
        doThrow(new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS))
                .when(categoryService).delete(1L);

        mockMvc.perform(delete("/api/admin/categories/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_400_HAS_PRODUCTS"));
    }
}
