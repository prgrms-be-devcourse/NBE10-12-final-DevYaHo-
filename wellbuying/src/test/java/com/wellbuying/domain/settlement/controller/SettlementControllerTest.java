package com.wellbuying.domain.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import com.wellbuying.domain.settlement.service.SettlementQueryService;
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
// (조합 로직은 SettlementQueryServiceTest가 다룬다)
@WebMvcTest(SettlementController.class)
@AutoConfigureMockMvc(addFilters = false)
class SettlementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettlementQueryService settlementQueryService;

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
    void 내_정산_내역은_로그인_회원_id로_서비스를_호출하고_목록을_반환한다() throws Exception {
        login(5L);
        SettlementResponse row = new SettlementResponse(1L, 42L, "제주 감귤 공동구매", 5L, "푸른살림",
                3, 100_000L, 5_000L, 95_000L, SettlementStatus.CONFIRMED, LocalDateTime.now());
        when(settlementQueryService.getMySettlements(eq(5L), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        mockMvc.perform(get("/api/settlements/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].groupBuyTitle").value("제주 감귤 공동구매"))
                .andExpect(jsonPath("$.content[0].payout").value(95000));

        verify(settlementQueryService).getMySettlements(eq(5L), any());
    }
}
