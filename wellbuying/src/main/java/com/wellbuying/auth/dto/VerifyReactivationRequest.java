package com.wellbuying.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyReactivationRequest(
        @NotBlank @Email String email,
        @NotBlank String code
) {
}
