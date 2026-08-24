package com.wellbuying.domain.seller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerSignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 255) String name,
        @NotBlank String bankCode,
        @NotBlank String bankName,
        @NotBlank String accountNumber,
        @NotBlank String accountHolder,
        String companyName
) {
}
