package com.wellbuying.groupbuy.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record GroupBuyUpdateRequest(
        @Size(max = 200) String title,
        LocalDateTime endAt
) {
}
