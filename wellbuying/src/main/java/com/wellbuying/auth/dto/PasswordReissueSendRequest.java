package com.wellbuying.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordReissueSendRequest(
        @NotBlank @Email String email
) {
}
