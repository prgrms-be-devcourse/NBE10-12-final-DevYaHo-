package com.wellbuying.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

// 토스 결제창이 successUrl로 돌려준 값들. authKey는 1회용이며 서버가 빌링키로 교환한다
public record BillingKeyRegisterRequest(
        @NotBlank String authKey,
        @NotBlank String customerKey
) {
}
