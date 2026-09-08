package com.wellbuying.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 판매자 본인 상품 삭제 시 사유 입력 (관리자 강제 삭제와 동일한 정책)
public record ProductDeleteRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
