package com.wellbuying.domain.address.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BuyerAddressCreateRequest(
        @NotBlank @Size(max = 255) String address,
        @Size(max = 255) String addressDetail,
        // 2015년부터 시행된 새 우편번호 체계 - 숫자 5자리 고정
        @NotBlank @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다") String zipcode,
        // 최초 등록이거나 true면 기본 배송지로 지정 - 기존 기본 배송지는 자동 해제된다
        boolean isDefault
) {
}
