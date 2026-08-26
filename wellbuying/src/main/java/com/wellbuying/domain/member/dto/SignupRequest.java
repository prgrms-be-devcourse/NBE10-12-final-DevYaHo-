package com.wellbuying.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 255) String name,
        // SMS 인증 도입 전까지는 필수값으로 강제하지 않으므로 @NotBlank는 붙이지 않고, 입력 시 형식/길이만 검증
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        @Size(max = 20)
        String phoneNumber
) {
}
