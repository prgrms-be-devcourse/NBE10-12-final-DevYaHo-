package com.wellbuying.domain.groupbuy.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.groupbuy.domain.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartMeResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPartResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPriceResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyStatusResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyUpdateRequest;
import com.wellbuying.domain.groupbuy.service.GroupBuyParticipationService;
import com.wellbuying.domain.groupbuy.service.GroupBuyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groupBuys")
public class GroupBuyController {

    private final GroupBuyService groupBuyService;
    private final GroupBuyParticipationService groupBuyParticipationService;

    public GroupBuyController(GroupBuyService groupBuyService,
            GroupBuyParticipationService groupBuyParticipationService) {
        this.groupBuyService = groupBuyService;
        this.groupBuyParticipationService = groupBuyParticipationService;
    }

    // 공동구매 생성 (생산자)
    @PostMapping
    public ResponseEntity<GroupBuyDetailResponse> create(@AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody GroupBuyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(groupBuyService.create(member.memberId(), request));
    }

    // 상세 조회 (잘 안 바뀌는 정보만)
    @GetMapping("/{id}")
    public ResponseEntity<GroupBuyDetailResponse> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(groupBuyService.getDetail(id));
    }

    // 실시간 상태 조회 (참여자 수, 잔여 수량, 남은 시간)
    @GetMapping("/{id}/status")
    public ResponseEntity<GroupBuyStatusResponse> getStatus(@PathVariable Long id) {
        return ResponseEntity.ok(groupBuyService.getStatus(id));
    }

    // 목록/검색
    @GetMapping
    public ResponseEntity<Page<GroupBuySummaryResponse>> list(
            @RequestParam(required = false) GroupBuyStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(groupBuyService.list(status, pageable));
    }

    // 정보 수정
    @PatchMapping("/{id}")
    public ResponseEntity<GroupBuyDetailResponse> update(@AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long id, @Valid @RequestBody GroupBuyUpdateRequest request) {
        return ResponseEntity.ok(groupBuyService.update(member.memberId(), id, request));
    }

    // 취소 (시작 전에만)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable Long id) {
        groupBuyService.cancel(member.memberId(), id);
        return ResponseEntity.noContent().build();
    }

    // 가격 구간 조회
    @GetMapping("/{id}/price")
    public ResponseEntity<List<GroupBuyPriceResponse>> getPriceTiers(@PathVariable Long id) {
        return ResponseEntity.ok(groupBuyService.getPriceTiers(id));
    }

    // 참여 신청
    @PostMapping("/{id}/part")
    public ResponseEntity<GroupBuyPartResponse> participate(@AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long id, @Valid @RequestBody GroupBuyPartCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupBuyParticipationService.participate(member.memberId(), id, request));
    }

    // 참여 취소
    @DeleteMapping("/{id}/part/{partId}")
    public ResponseEntity<Void> cancelParticipation(@AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long id, @PathVariable Long partId) {
        groupBuyParticipationService.cancelParticipation(member.memberId(), id, partId);
        return ResponseEntity.noContent().build();
    }

    // 내 참여 내역 여부
    @GetMapping("/{id}/part/me")
    public ResponseEntity<GroupBuyPartMeResponse> myParticipation(@AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long id) {
        return ResponseEntity.ok(groupBuyParticipationService.myParticipation(member.memberId(), id));
    }
}
