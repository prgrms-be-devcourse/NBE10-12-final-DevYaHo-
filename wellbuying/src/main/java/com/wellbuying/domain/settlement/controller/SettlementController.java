package com.wellbuying.domain.settlement.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.settlement.dto.SettlementListItemResponse;
import com.wellbuying.domain.settlement.dto.SettlementListStatus;
import com.wellbuying.domain.settlement.dto.SettlementMonthlySummaryResponse;
import com.wellbuying.domain.settlement.dto.SettlementParticipantResponse;
import com.wellbuying.domain.settlement.dto.SettlementProgressResponse;
import com.wellbuying.domain.settlement.dto.SettlementTrendGranularity;
import com.wellbuying.domain.settlement.dto.SettlementTrendPointResponse;
import com.wellbuying.domain.settlement.service.SettlementDetailService;
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
import org.springframework.web.bind.annotation.PathVariable;
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
    private final SettlementDetailService settlementDetailService;

    public SettlementController(SettlementQueryService settlementQueryService,
            SettlementStatsService settlementStatsService, SettlementDetailService settlementDetailService) {
        this.settlementQueryService = settlementQueryService;
        this.settlementStatsService = settlementStatsService;
        this.settlementDetailService = settlementDetailService;
    }

    // 판매자 본인의 정산 내역 - producerId == 로그인한 회원 id.
    // year/month는 공동구매 성사월(finalizedAt) 기준이며, 둘 중 하나라도 생략하면 이번 달로 본다.
    // status 생략 시 대기중(PENDING)+완료(COMPLETED) 전체를 합쳐서 보여준다
    @Operation(summary = "내 정산 내역 목록 - 월별(공동구매 성사월 기준) + 상태 필터(PENDING/COMPLETED)")
    @GetMapping("/me")
    public ResponseEntity<Page<SettlementListItemResponse>> getMySettlements(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) SettlementListStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                settlementQueryService.getMySettlements(member.memberId(), year, month, status, pageable));
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

    // 정산 대기중 건 상세 - 몇 명 중 몇 명이 결제했는지 진행도
    @Operation(summary = "정산 대기중 건 상세 - 결제 진행도(확정 참여자 중 결제 완료 수)")
    @GetMapping("/me/{groupBuyId}/progress")
    public ResponseEntity<SettlementProgressResponse> getProgress(
            @AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long groupBuyId) {
        return ResponseEntity.ok(settlementDetailService.getProgress(member.memberId(), groupBuyId));
    }

    // 정산 완료 건 상세 - 결제한 참여자 명단
    @Operation(summary = "정산 완료 건 상세 - 결제한 참여자 명단")
    @GetMapping("/me/{groupBuyId}/participants")
    public ResponseEntity<List<SettlementParticipantResponse>> getParticipants(
            @AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long groupBuyId) {
        return ResponseEntity.ok(settlementDetailService.getParticipants(member.memberId(), groupBuyId));
    }
}
