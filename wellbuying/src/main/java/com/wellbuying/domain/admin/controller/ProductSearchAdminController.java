package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.product.search.ProductSearchReconcileScheduler;
import com.wellbuying.domain.product.search.ProductSearchReconcileStatusResponse;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/search")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "관리자 - 검색", description = "검색 인덱스 관리")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class ProductSearchAdminController {

    private final ProductSearchReconcileScheduler reconcileScheduler;
    private final Executor searchReconcileExecutor;

    public ProductSearchAdminController(
            ProductSearchReconcileScheduler reconcileScheduler,
            @Qualifier("searchReconcileExecutor") Executor searchReconcileExecutor) {
        this.reconcileScheduler = reconcileScheduler;
        this.searchReconcileExecutor = searchReconcileExecutor;
    }

    @Operation(summary = "검색 인덱스 보정 배치 상태 조회")
    @GetMapping("/reconcile/status")
    public ResponseEntity<ProductSearchReconcileStatusResponse> getReconcileStatus() {
        return ResponseEntity.ok(ProductSearchReconcileStatusResponse.from(reconcileScheduler));
    }

    // 검색 인덱스 정합성 보정 배치를 즉시 1회 실행 (관리자 전용, 테스트/운영 대응용)
    // searchReconcileExecutor(단일 스레드)로 비동기 실행 - 이미 실행 중이면 1건 대기, 추가 요청은 조용히 무시됨
    @Operation(summary = "검색 인덱스 보정 배치 즉시 실행")
    @PostMapping("/reconcile")
    public ResponseEntity<Void> triggerReconcile() {
        searchReconcileExecutor.execute(reconcileScheduler::reconcile);
        return ResponseEntity.accepted().build();
    }
}