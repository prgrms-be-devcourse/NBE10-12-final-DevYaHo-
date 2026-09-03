package com.wellbuying.domain.admin.controller;

import com.wellbuying.domain.member.dto.MemberSummaryResponse;
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.service.MemberService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 전용 회원 목록 조회 API - ADMIN role만 접근 가능
@RestController
@RequestMapping("/api/admin/members")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "관리자 - 회원", description = "회원 목록 조회")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AdminMemberController {

    private final MemberService memberService;

    public AdminMemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // role/status로 선택적으로 필터링한 회원 목록 조회
    @Operation(summary = "role/status로 선택적으로 필터링한 회원 목록 조회")
    @GetMapping
    public ResponseEntity<Page<MemberSummaryResponse>> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) MemberStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(memberService.findMembers(role, status, pageable));
    }
}
