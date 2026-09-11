package com.wellbuying.domain.settlement.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.settlement.dto.SettlementResponse;
import com.wellbuying.domain.settlement.service.SettlementQueryService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
@Tag(name = "정산", description = "판매자 정산 내역 조회")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class SettlementController {

    private final SettlementQueryService settlementQueryService;

    public SettlementController(SettlementQueryService settlementQueryService) {
        this.settlementQueryService = settlementQueryService;
    }

    // 판매자 본인의 정산 내역 - producerId == 로그인한 회원 id. 정렬은 최신 확정순으로 서비스가 고정한다
    @Operation(summary = "내 정산 내역 목록 - 확정된 공동구매별 정산(총 매출/수수료/지급 예정액)")
    @GetMapping("/me")
    public ResponseEntity<Page<SettlementResponse>> getMySettlements(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(settlementQueryService.getMySettlements(member.memberId(), pageable));
    }
}
