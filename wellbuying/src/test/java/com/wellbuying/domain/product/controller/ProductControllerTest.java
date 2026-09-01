package com.wellbuying.domain.product.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.domain.product.dto.ProductDetailResponse;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.search.ProductSearchResponse;
import com.wellbuying.domain.product.service.ProductSearchService;
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

    @MockitoBean
    private ProductSearchService productSearchService;

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

    // 상품 단건 조회 시 200과 함께 상세 필드가 반환된다
    @Test
    void getProduct_존재하는_상품이면_상세정보를_반환한다() throws Exception {
        ProductDetailResponse response = new ProductDetailResponse(1L, "상품", "설명", 10000, "url", true);
        when(productService.getDetail(1L)).thenReturn(response);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("상품"))
                .andExpect(jsonPath("$.description").value("설명"));
    }

    // keyword 파라미터로 검색 시 200과 함께 결과 목록이 반환된다
    @Test
    void searchProducts_키워드로_검색하면_결과를_반환한다() throws Exception {
        ProductSearchResponse response = new ProductSearchResponse(1L, "비타민C", 5000, "url", 0L);
        Slice<ProductSearchResponse> slice = new SliceImpl<>(List.of(response), PageRequest.of(0, 20), false);
        when(productSearchService.search(any(), any(), any(int.class), any(int.class))).thenReturn(slice);

        mockMvc.perform(get("/api/products/search").param("keyword", "비타민"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("비타민C"));
    }

    // 빈 문자열 keyword는 @NotBlank 위반 → 400
    @Test
    void searchProducts_빈_키워드로_검색하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/search").param("keyword", ""))
                .andExpect(status().isBadRequest());
    }

    // size가 100 초과이면 @Max(100) 위반 → 400
    @Test
    void searchProducts_size가_100_초과이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/search").param("keyword", "비타민").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    // page가 -1이면 @Min(0) 위반 → 400, 응답 메시지에 "page" 포함
    @Test
    void searchProducts_page가_음수이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/search").param("keyword", "비타민").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    // size가 0이면 @Min(1) 위반 → 400, 응답 메시지에 "size" 포함
    @Test
    void searchProducts_size가_0이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/search").param("keyword", "비타민").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("size")));
    }

    // 공백만 있는 keyword는 @NotBlank 위반 → 400, 응답 메시지에 "keyword" 포함
    @Test
    void searchProducts_공백_키워드로_검색하면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/search").param("keyword", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("keyword")));
    }

    // keyword, page 둘 다 유효하지 않으면 두 파라미터의 에러 메시지가 모두 응답에 포함되는지 검증
    // (여러 위반 항목을 결합하는 이번 변경의 핵심 동작을 검증)
    @Test
    void searchProducts_여러_파라미터가_유효하지_않으면_모든_에러_메시지를_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/search").param("keyword", " ").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("keyword")))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    // 잘못된 sort 값은 enum 변환 실패 → MethodArgumentNotValidException → 400
    @Test
    void searchProducts_잘못된_sort_타입이면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/products/search")
                        .param("keyword", "비타민")
                        .param("sort", "INVALID_TYPE"))
                .andExpect(status().isBadRequest());
    }
}