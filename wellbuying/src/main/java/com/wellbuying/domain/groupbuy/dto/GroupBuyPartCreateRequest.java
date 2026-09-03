package com.wellbuying.domain.groupbuy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GroupBuyPartCreateRequest(
        @Positive int quantity,
        // 회원 주소록(buyer_address)에 등록된 배송지 중 이번 참여에 쓸 항목의 ID
        @NotNull Long buyerAddressId
) {
}
