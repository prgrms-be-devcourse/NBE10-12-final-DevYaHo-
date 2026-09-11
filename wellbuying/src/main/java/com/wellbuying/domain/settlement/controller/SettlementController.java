package com.wellbuying.domain.settlement.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.settlement.dto.SettlementMonthlySummaryResponse;
import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.dto.SettlementTrendGranularity;
import com.wellbuying.domain.settlement.dto.SettlementTrendPointResponse;
import com.wellbuying.domain.settlement.service.SettlementQueryService;
import com.wellbuying.domain.settlement.service.SettlementStatsService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@Tag(name = "정산", description = "판매자 정산 내역 조회")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SettlementController {

    private final SettlementQueryService settlementQueryService;
    private final SettlementStatsService settlementStatsService;

    public SettlementController(SettlementQueryService settlementQueryService,
            SettlementStatsService settlementStatsService) {
        this.settlementQueryService = settlementQueryService;
        this.settlementStatsService = settlementStatsService;
    }

    // 판매자 본인의 정산 내역 - producerId == 로그인한 회원 id. 정렬은 최신 확정순으로 서비스가 고정한다
    @Operation(summary = "내 정산 내역 목록 - 확정된 공동구매별 정산(총 매출/수수료/지급 예정액)")
    @GetMapping("/me")
    public ResponseEntity<Page<SettlementResponse>> getMySettlements(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(settlementQueryService.getMySettlements(member.memberId(), pageable));
    }

    // 매출 추이 그래프 - settlement_item.paidAt 기준(정산 확정 여부 무관), 이번 달 포함 최근 12개월
    @Operation(summary = "매출 추이 그래프 - 이번 달 포함 최근 12개월을 주/월 단위로 집계")
    @GetMapping("/me/trend")
    public ResponseEntity<List<SettlementTrendPointResponse>> getTrend(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(defaultValue = "MONTHLY") SettlementTrendGranularity granularity) {
        return ResponseEntity.ok(settlementStatsService.getTrend(member.memberId(), granularity));
    }

    // 이번 달 매출 요약 카드 3개 (이번 달 매출 / 정산 대기 중 / 이번 달 정산 완료) - 각 필드 의미는
    // SettlementMonthlySummaryResponse 주석 참고
    @Operation(summary = "이번 달 매출 요약 - 이번 달 매출 / 정산 대기 중(ACCRUED) / 이번 달 정산 완료")
    @GetMapping("/me/monthly-summary")
    public ResponseEntity<SettlementMonthlySummaryResponse> getMonthlySummary(
            @AuthenticationPrincipal AuthenticatedMember member) {
        return ResponseEntity.ok(settlementStatsService.getMonthlySummary(member.memberId()));
    }
}
