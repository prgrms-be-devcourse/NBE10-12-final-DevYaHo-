package com.wellbuying.domain.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.settlement.dto.SettlementListItemResponse;
import com.wellbuying.domain.settlement.dto.SettlementListStatus;
import com.wellbuying.domain.settlement.dto.SettlementMonthlySummaryResponse;
import com.wellbuying.domain.settlement.dto.SettlementTrendGranularity;
import com.wellbuying.domain.settlement.dto.SettlementTrendPointResponse;
import com.wellbuying.domain.settlement.service.SettlementQueryService;
import com.wellbuying.domain.settlement.service.SettlementStatsService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 컨트롤러 계층만 띄워 로그인 회원 id가 서비스로 전달되고 응답이 JSON으로 나가는지만 검증
// (조합 로직은 SettlementQueryServiceTest / SettlementStatsServiceTest가 다룬다)
@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService settlementQueryService;

    @MockitoBean
    private SettlementStatsService settlementStatsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void login(long memberId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(memberId, "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void 내_정산_내역은_로그인_회원_id와_년월_상태를_그대로_서비스에_전달한다() throws Exception {
        login(5L);
        SettlementListItemResponse row = new SettlementListItemResponse(1L, 42L, "제주 감귤 공동구매", 5L, "푸른살림",
                3, 100_000L, 5_000L, 95_000L, SettlementListStatus.COMPLETED, LocalDateTime.now(),
                LocalDateTime.now());
        when(settlementQueryService.getMySettlements(eq(5L), eq(2026), eq(9), eq(SettlementListStatus.COMPLETED),
                any())).thenReturn(new PageImpl<>(List.of(row)));

        mockMvc.perform(get("/api/settlements/me")
                        .param("year", "2026").param("month", "9").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].groupBuyTitle").value("제주 감귤 공동구매"))
                .andExpect(jsonPath("$.content[0].payout").value(95000));

        verify(settlementQueryService).getMySettlements(eq(5L), eq(2026), eq(9), eq(SettlementListStatus.COMPLETED),
                any());
    }

    @Test
    void 내_정산_내역은_year_month_status_생략시_null로_전달한다() throws Exception {
        login(5L);
        when(settlementQueryService.getMySettlements(eq(5L), eq(null), eq(null), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/settlements/me")).andExpect(status().isOk());

        verify(settlementQueryService).getMySettlements(eq(5L), eq(null), eq(null), eq(null), any());
    }

    @Test
    void 매출_추이는_granularity를_그대로_서비스에_전달한다() throws Exception {
        login(5L);
        when(settlementStatsService.getTrend(eq(5L), eq(SettlementTrendGranularity.WEEKLY)))
                .thenReturn(List.of(new SettlementTrendPointResponse(LocalDateTime.of(2026, 9, 1, 0, 0), 100_000L, 3)));

        mockMvc.perform(get("/api/settlements/me/trend").param("granularity", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalSales").value(100000))
                .andExpect(jsonPath("$[0].itemCount").value(3));

        verify(settlementStatsService).getTrend(eq(5L), eq(SettlementTrendGranularity.WEEKLY));
    }

    @Test
    void 매출_추이는_granularity_생략시_MONTHLY로_기본값이_적용된다() throws Exception {
        login(5L);
        when(settlementStatsService.getTrend(eq(5L), eq(SettlementTrendGranularity.MONTHLY))).thenReturn(List.of());

        mockMvc.perform(get("/api/settlements/me/trend")).andExpect(status().isOk());

        verify(settlementStatsService).getTrend(eq(5L), eq(SettlementTrendGranularity.MONTHLY));
    }

    @Test
    void 이번달_요약은_로그인_회원_id로_서비스를_호출하고_반환한다() throws Exception {
        login(5L);
        SettlementMonthlySummaryResponse summary = new SettlementMonthlySummaryResponse(
                "2026-09", 1_000_000L, 10, 800_000L, 150_000L, 3, 700_000L, 6);
        when(settlementStatsService.getMonthlySummary(5L)).thenReturn(summary);

        mockMvc.perform(get("/api/settlements/me/monthly-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.pendingAmount").value(150000));

        verify(settlementStatsService).getMonthlySummary(5L);
    }
}
