package com.wellbuying.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminActionReasonRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
