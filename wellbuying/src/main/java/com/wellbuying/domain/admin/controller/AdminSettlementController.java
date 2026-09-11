package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import com.wellbuying.domain.settlement.service.SettlementQueryService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public AdminSettlementController(SettlementQueryService settlementQueryService) {
        this.settlementQueryService = settlementQueryService;
    }

    @Operation(summary = "전체 정산 내역 목록 - status로 필터 (미지정 시 전체), 최신 확정순")
    @GetMapping
    public ResponseEntity<Page<SettlementResponse>> list(
            @RequestParam(required = false) SettlementStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(settlementQueryService.getAllSettlements(status, pageable));
    }
}
