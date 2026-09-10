package com.wellbuying.domain.address.service;

import com.wellbuying.domain.address.dto.BuyerAddressCreateRequest;
import com.wellbuying.domain.address.dto.BuyerAddressResponse;
import com.wellbuying.domain.address.entity.BuyerAddress;
import com.wellbuying.domain.address.repository.BuyerAddressRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
        // 요청에서 지정했거나, 회원의 첫 배송지면 자동으로 기본 배송지가 된다
        boolean makeDefault = request.isDefault() || !buyerAddressRepository.existsByMemberId(memberId);
        if (makeDefault) {
            buyerAddressRepository.findByMemberIdAndIsDefaultTrue(memberId)
                    .ifPresent(BuyerAddress::unmarkAsDefault);
        }
        BuyerAddress buyerAddress = buyerAddressRepository.save(
                BuyerAddress.create(memberId, request.address(), request.addressDetail(), request.zipcode(),
                        makeDefault));
        return BuyerAddressResponse.of(buyerAddress);
    }

    @Transactional(readOnly = true)
    public List<BuyerAddressResponse> list(Long memberId) {
        return buyerAddressRepository.findByMemberIdOrderByIsDefaultDescIdDesc(memberId).stream()
                .map(BuyerAddressResponse::of)
                .toList();
    }

    // 캐시(findOwner)에 이 주소록 항목이 남아있으면 삭제 이후에도 소유권 검증을 통과시켜버리므로,
    // 삭제와 같은 트랜잭션에서 반드시 함께 지운다
    @Transactional
    @CacheEvict(value = "buyerAddressOwner", key = "#addressId")
    public void delete(Long memberId, Long addressId) {
        BuyerAddress buyerAddress = buyerAddressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUYER_ADDRESS_NOT_FOUND));
        if (!buyerAddress.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.BUYER_ADDRESS_FORBIDDEN);
        }
        buyerAddressRepository.delete(buyerAddress);
    }

    // 참여 API(GroupBuyParticipationService)가 매 요청 검증하는 "이 주소록 항목의 소유자가 누구인가"를
    // 캐싱한다 - 배송지는 삭제 전까지 소유자가 바뀌지 않으므로 적중률이 높다. 참여 요청마다 RDS 왕복을
    // 하나 없애는 게 목적이다. 결과를 Long 원시 래퍼로 바로 반환하지 않고 record로 감싸는 이유:
    // Redis 캐시가 쓰는 GenericJackson2JsonRedisSerializer(CacheConfig 참고)는 POJO/record는 타입 정보를
    // 같이 저장해 정확히 복원하지만, Long 같은 박싱 타입은 JSON 숫자로만 저장되어 다시 읽을 때 값 범위에
    // 따라 Integer로 역직렬화될 수 있다 - 이 경우 메서드 반환 타입(Long)과 안 맞아 캐시 히트 시
    // ClassCastException이 난다(실제 통합 테스트로 재현 확인). TTL(기본 24시간, CacheConfig 참고)은
    // delete()의 evict를 놓쳤을 때의 최후 안전망일 뿐이고, 정상 경로에서는 delete() 시점에 즉시
    // 무효화된다. 존재하지 않는 addressId는 null을 반환하고, 호출 측이 이를 NOT_FOUND로 해석한다
    public record BuyerAddressOwner(Long memberId) {
    }

    @Cacheable(value = "buyerAddressOwner", key = "#addressId")
    @Transactional(readOnly = true)
    public BuyerAddressOwner findOwner(Long addressId) {
        return buyerAddressRepository.findById(addressId)
                .map(a -> new BuyerAddressOwner(a.getMemberId()))
                .orElse(null);
    }

    @Transactional
    public void setDefault(Long memberId, Long addressId) {
        BuyerAddress buyerAddress = buyerAddressRepository.findById(addressId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUYER_ADDRESS_NOT_FOUND));
        if (!buyerAddress.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.BUYER_ADDRESS_FORBIDDEN);
        }
        if (buyerAddress.isDefault()) {
            return;
        }
        buyerAddressRepository.findByMemberIdAndIsDefaultTrue(memberId)
                .ifPresent(BuyerAddress::unmarkAsDefault);
        buyerAddress.markAsDefault();
    }
}
