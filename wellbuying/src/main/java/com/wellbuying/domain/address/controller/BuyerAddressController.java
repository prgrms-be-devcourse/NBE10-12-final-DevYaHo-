package com.wellbuying.domain.address.controller;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.address.dto.BuyerAddressCreateRequest;
import com.wellbuying.domain.address.dto.BuyerAddressResponse;
import com.wellbuying.domain.address.service.BuyerAddressService;
import com.wellbuying.global.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members/me/addresses")
@Tag(name = "배송지", description = "회원 배송지 주소록 등록/조회/삭제")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class BuyerAddressController {

    private final BuyerAddressService buyerAddressService;

    public BuyerAddressController(BuyerAddressService buyerAddressService) {
        this.buyerAddressService = buyerAddressService;
    }

    // 배송지 등록 - 공동구매 참여 시 buyerAddressId로 참조할 주소록 항목을 추가한다
    @Operation(summary = "배송지 등록")
    @PostMapping
    public ResponseEntity<BuyerAddressResponse> create(@AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody BuyerAddressCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(buyerAddressService.create(member.memberId(), request));
    }

    // 내 배송지 목록 조회
    @Operation(summary = "배송지 목록 조회")
    @GetMapping
    public ResponseEntity<List<BuyerAddressResponse>> list(@AuthenticationPrincipal AuthenticatedMember member) {
        return ResponseEntity.ok(buyerAddressService.list(member.memberId()));
    }

    // 배송지 삭제 - 본인 소유가 아니면 403
    @Operation(summary = "배송지 삭제")
    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long addressId) {
        buyerAddressService.delete(member.memberId(), addressId);
        return ResponseEntity.noContent().build();
    }
}
