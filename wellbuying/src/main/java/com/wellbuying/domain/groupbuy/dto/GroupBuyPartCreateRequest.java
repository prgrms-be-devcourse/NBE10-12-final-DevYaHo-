package com.wellbuying.domain.groupbuy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GroupBuyPartCreateRequest(
        @Positive int quantity,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 255) String addressDetail,
        // 2015년부터 시행된 새 우편번호 체계 - 숫자 5자리 고정
        @NotBlank @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다") String zipcode
) {
}
