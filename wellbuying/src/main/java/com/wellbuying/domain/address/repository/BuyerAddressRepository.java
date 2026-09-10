package com.wellbuying.domain.address.repository;

import com.wellbuying.domain.address.entity.BuyerAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyerAddressRepository extends JpaRepository<BuyerAddress, Long> {

    List<BuyerAddress> findByMemberIdOrderByIdDesc(Long memberId);

    List<BuyerAddress> findByMemberIdOrderByIsDefaultDescIdDesc(Long memberId);

    Optional<BuyerAddress> findByMemberIdAndIsDefaultTrue(Long memberId);

    boolean existsByMemberId(Long memberId);
}
