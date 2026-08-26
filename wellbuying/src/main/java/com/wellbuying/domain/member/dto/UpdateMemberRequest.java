package com.wellbuying.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String profileImageUrl,
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        @Size(max = 20)
        String phoneNumber
) {
}
