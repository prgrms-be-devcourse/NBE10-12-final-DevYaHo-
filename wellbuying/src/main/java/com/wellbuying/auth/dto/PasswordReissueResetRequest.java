package com.wellbuying.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordReissueResetRequest(
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z])(?=.*[^a-zA-Z0-9]).{8,100}$",
                message = "비밀번호는 숫자, 영문자, 특수문자를 각각 최소 1개 포함하여 8자 이상이어야 합니다.")
        String newPassword
) {
}
