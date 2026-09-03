package com.wellbuying.domain.address.dto;

import com.wellbuying.domain.address.entity.BuyerAddress;
import java.time.LocalDateTime;

public record BuyerAddressResponse(
        Long id,
        String address,
        String addressDetail,
        String zipcode,
        LocalDateTime createdAt
) {

    public static BuyerAddressResponse of(BuyerAddress buyerAddress) {
        return new BuyerAddressResponse(
                buyerAddress.getId(),
                buyerAddress.getAddress(),
                buyerAddress.getAddressDetail(),
                buyerAddress.getZipcode(),
                buyerAddress.getCreatedAt());
    }
}
