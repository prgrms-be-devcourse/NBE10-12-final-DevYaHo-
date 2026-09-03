package com.wellbuying.domain.address.service;

import com.wellbuying.domain.address.dto.BuyerAddressCreateRequest;
import com.wellbuying.domain.address.dto.BuyerAddressResponse;
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuyerAddressService {

    private final BuyerAddressRepository buyerAddressRepository;

    public BuyerAddressService(BuyerAddressRepository buyerAddressRepository) {
        this.buyerAddressRepository = buyerAddressRepository;
    }

    @Transactional
    public BuyerAddressResponse create(Long memberId, BuyerAddressCreateRequest request) {
        BuyerAddress buyerAddress = buyerAddressRepository.save(
                BuyerAddress.create(memberId, request.address(), request.addressDetail(), request.zipcode()));
        return BuyerAddressResponse.of(buyerAddress);
    }

    @Transactional(readOnly = true)
    public List<BuyerAddressResponse> list(Long memberId) {
        return buyerAddressRepository.findByMemberIdOrderByIdDesc(memberId).stream()
                .map(BuyerAddressResponse::of)
                .toList();
    }

    @Transactional
    public void delete(Long memberId, Long addressId) {
        BuyerAddress buyerAddress = buyerAddressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUYER_ADDRESS_NOT_FOUND));
        if (!buyerAddress.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.BUYER_ADDRESS_FORBIDDEN);
        }
        buyerAddressRepository.delete(buyerAddress);
    }
}
