package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuySuspensionRequestResponse;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionStatus;
import com.wellbuying.domain.groupbuy.service.GroupBuyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 공동구매 조회/판매정지 승인·반려 API - ADMIN role만 접근 가능 (AdminSellerController와 동일 패턴)
@RestController
@RequestMapping("/api/admin/groupBuys")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGroupBuyController {

    private final GroupBuyService groupBuyService;

    public AdminGroupBuyController(GroupBuyService groupBuyService) {
        this.groupBuyService = groupBuyService;
    }

    // 전체 공동구매 목록 조회 (상태 필터 선택)
    @GetMapping
    public ResponseEntity<Page<GroupBuySummaryResponse>> list(
            @RequestParam(required = false) GroupBuyStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(groupBuyService.list(status, pageable));
    }

    // 상태별 판매정지 요청 목록 조회 (예: ?status=PENDING으로 처리 대기 목록 조회)
    @GetMapping("/suspension-requests")
    public ResponseEntity<Page<GroupBuySuspensionRequestResponse>> listSuspensionRequests(
            @RequestParam GroupBuySuspensionStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(groupBuyService.listSuspensionRequests(status, pageable));
    }

    // 판매정지 요청 승인 - 대상 공동구매를 suspended=true로 전환
    @PostMapping("/suspension-requests/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        groupBuyService.approveSuspensionRequest(id);
        return ResponseEntity.noContent().build();
    }

    // 판매정지 요청 반려
    @PostMapping("/suspension-requests/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id) {
        groupBuyService.rejectSuspensionRequest(id);
        return ResponseEntity.noContent().build();
    }
}
