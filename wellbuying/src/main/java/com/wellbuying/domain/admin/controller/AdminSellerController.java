package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.seller.dto.SellerInfoResponse;
import com.wellbuying.domain.seller.entity.SellerStatus;
import com.wellbuying.domain.seller.service.SellerInfoService;
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

// 관리자 전용 셀러 승인/거절 API - ADMIN role만 접근 가능 (이 코드베이스에서 @PreAuthorize를 최초로 도입)
@RestController
@RequestMapping("/api/admin/sellers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSellerController {

    private final SellerInfoService sellerInfoService;

    public AdminSellerController(SellerInfoService sellerInfoService) {
        this.sellerInfoService = sellerInfoService;
    }

    // 상태별 셀러 신청 목록 조회 (예: ?status=PENDING으로 승인 대기 목록 조회)
    @GetMapping
    public ResponseEntity<Page<SellerInfoResponse>> list(@RequestParam SellerStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(sellerInfoService.findByStatus(status, pageable));
    }

    // 셀러 승인 - SELLER_INFO.status를 ACTIVE로, MEMBERS.role을 SELLER로 변경
    @PostMapping("/{sellerId}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long sellerId) {
        sellerInfoService.approve(sellerId);
        return ResponseEntity.noContent().build();
    }

    // 셀러 거절 - SELLER_INFO.status를 TERMINATED로 변경 (role은 BUYER 유지)
    @PostMapping("/{sellerId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long sellerId) {
        sellerInfoService.reject(sellerId);
        return ResponseEntity.noContent().build();
    }
}
