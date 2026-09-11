package com.wellbuying.domain.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import com.wellbuying.domain.settlement.service.SettlementQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 컨트롤러 계층만 띄워 status 쿼리 파라미터가 서비스로 전달되고 응답이 JSON으로 나가는지만 검증
// (@PreAuthorize('ADMIN') 실제 차단은 addFilters=false라 여기서 검증되지 않는다 - 통합 테스트 소관)
@WebMvcTest(AdminSettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminSettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService settlementQueryService;

    private SettlementResponse row() {
        return new SettlementResponse(1L, 42L, "제주 감귤 공동구매", 5L, "푸른살림",
                3, 100_000L, 5_000L, 95_000L, SettlementStatus.CONFIRMED, LocalDateTime.now());
    }

    @Test
    void status_파라미터가_있으면_그대로_서비스로_전달한다() throws Exception {
        when(settlementQueryService.getAllSettlements(eq(SettlementStatus.CONFIRMED), any()))
                .thenReturn(new PageImpl<>(List.of(row())));

        mockMvc.perform(get("/api/admin/settlements").param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].producerName").value("푸른살림"));

        verify(settlementQueryService).getAllSettlements(eq(SettlementStatus.CONFIRMED), any());
    }

    @Test
    void status_파라미터가_없으면_null로_전달한다() throws Exception {
        when(settlementQueryService.getAllSettlements(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(row())));

        mockMvc.perform(get("/api/admin/settlements"))
                .andExpect(status().isOk());

        verify(settlementQueryService).getAllSettlements(isNull(), any());
    }
}
