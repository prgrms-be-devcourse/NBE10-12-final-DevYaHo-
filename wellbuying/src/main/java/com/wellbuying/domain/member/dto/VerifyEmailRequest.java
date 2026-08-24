package com.wellbuying.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank String code
) {
}
