package com.wellbuying.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMemberRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String profileImageUrl
) {
}
