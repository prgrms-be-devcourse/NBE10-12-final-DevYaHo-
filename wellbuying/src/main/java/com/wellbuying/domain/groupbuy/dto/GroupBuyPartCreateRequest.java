package com.wellbuying.domain.groupbuy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record GroupBuyPartCreateRequest(
        @Positive int quantity,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 255) String addressDetail,
        @NotBlank @Size(max = 20) String zipcode
) {
}
