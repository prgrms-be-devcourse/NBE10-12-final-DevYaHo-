package com.wellbuying.domain.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.service.ProductService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 컨트롤러 계층만 띄워서, 쿼리 파라미터가 서비스로 잘 전달되고 응답이 JSON으로 잘 나가는지만 검증 (서비스 로직은 ProductServiceTest에서 이미 검증함)
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    // 파라미터 없이 호출해도 200과 함께 목록이 반환된다
    @Test
    void getProducts_파라미터없이_호출해도_정상응답한다() throws Exception {
        ProductSummaryResponse response = new ProductSummaryResponse(1L, "상품", 10000, "url", 0L);
        Slice<ProductSummaryResponse> slice = new SliceImpl<>(List.of(response), PageRequest.of(0, 20), false);
        when(productService.getProducts(any(), any())).thenReturn(slice);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("상품"));
    }

    // category, minPrice 파라미터를 보내면 200으로 응답한다 (실제 필터링은 리포지토리 테스트에서 검증)
    @Test
    void getProducts_필터파라미터를_보내도_정상응답한다() throws Exception {
        Slice<ProductSummaryResponse> emptySlice = new SliceImpl<>(List.of(), PageRequest.of(0, 20), false);
        when(productService.getProducts(any(), any())).thenReturn(emptySlice);

        mockMvc.perform(get("/api/products").param("category", "1").param("minPrice", "1000"))
                .andExpect(status().isOk());
    }
}