package com.wellbuying.domain.seller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerSignupRequest(
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[^a-zA-Z0-9]).{8,100}$",
                message = "비밀번호는 숫자, 영문자, 특수문자를 각각 최소 1개 포함하여 8자 이상이어야 합니다.")
        String password,
        @NotBlank @Size(max = 255) String name,
        @NotBlank String bankCode,
        @NotBlank String bankName,
        @NotBlank String accountNumber,
        @NotBlank String accountHolder,
        String companyName
) {
}
