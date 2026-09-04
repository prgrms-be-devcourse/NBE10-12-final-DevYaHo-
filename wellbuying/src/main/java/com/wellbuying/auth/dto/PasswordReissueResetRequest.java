package com.wellbuying.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordReissueResetRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {
}
