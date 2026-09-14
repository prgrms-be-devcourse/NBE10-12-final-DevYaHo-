package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.settlement.dto.AdminSettlementSummaryResponse;
import com.wellbuying.domain.settlement.dto.AdminSettlementTrendPointResponse;
import com.wellbuying.domain.settlement.dto.SettlementListItemResponse;
import com.wellbuying.domain.settlement.dto.SettlementListStatus;
import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.dto.SettlementTrendGranularity;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 정산 내역 조회 - ADMIN role만 접근 가능 (게이트웨이 레벨 /api/admin/** 방어와 이중)
@RestController
@RequestMapping("/api/admin/settlements")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "관리자 - 정산", description = "전체 정산 내역 조회")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminSettlementController {

    private final SettlementQueryService settlementQueryService;
    private final SettlementStatsService settlementStatsService;

    public AdminSettlementController(SettlementQueryService settlementQueryService,
            SettlementStatsService settlementStatsService) {
        this.settlementQueryService = settlementQueryService;
        this.settlementStatsService = settlementStatsService;
    }

    @Operation(summary = "전체 정산 내역 목록 - status로 필터(미지정 시 전체), keyword로 공동구매 제목 검색, 최신 확정순")
    @GetMapping
    public ResponseEntity<Page<SettlementResponse>> list(
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(settlementQueryService.getAllSettlements(status, keyword, pageable));
    }

    // 생산자 대시보드(GET /api/settlements/me)와 같은 모양 - 판매자 구분 없이 전체를 대상으로 한다
    @Operation(summary = "전체 정산 내역 월별 리스트 - 월별(공동구매 성사월 기준) + 상태 필터(PENDING/COMPLETED) + keyword 검색")
    @GetMapping("/monthly")
    public ResponseEntity<Page<SettlementListItemResponse>> monthly(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) SettlementListStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(
                settlementQueryService.getAllSettlementsForMonth(year, month, status, keyword, pageable));
    }

    // 생산자 매출 추이 그래프(GET /api/settlements/me/trend)와 같은 모양 - 판매자 구분 없이 전체를 집계한다
    @Operation(summary = "매출 추이 그래프 - 이번 달 포함 최근 12개월을 주/월 단위로 집계, 수수료 추정치 포함")
    @GetMapping("/trend")
    public ResponseEntity<List<AdminSettlementTrendPointResponse>> trend(
            @RequestParam(defaultValue = "MONTHLY") SettlementTrendGranularity granularity) {
        return ResponseEntity.ok(settlementStatsService.getAdminTrend(granularity));
    }

    @Operation(summary = "정산 대시보드 상단 요약 카드 - 이번 달 매출, 정산 대기 건수/금액, 이번 달 정산 완료 건수/금액")
    @GetMapping("/summary")
    public ResponseEntity<AdminSettlementSummaryResponse> summary() {
        return ResponseEntity.ok(settlementQueryService.getAdminSummary());
    }
}
