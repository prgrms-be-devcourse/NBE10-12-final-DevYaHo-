package com.wellbuying.domain.seller.dto;

import jakarta.validation.constraints.NotBlank;

public record SellerApplyRequest(
        @NotBlank String bankCode,
        @NotBlank String bankName,
        @NotBlank String accountNumber,
        @NotBlank String accountHolder,
        String companyName
) {
}
