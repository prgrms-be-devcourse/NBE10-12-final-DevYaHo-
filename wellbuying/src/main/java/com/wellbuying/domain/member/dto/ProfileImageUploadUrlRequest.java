package com.wellbuying.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

public record ProfileImageUploadUrlRequest(@NotBlank String contentType) {
}
