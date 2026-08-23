package com.wellbuying.domain.seller.repository;

import com.wellbuying.domain.seller.entity.SellerInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerInfoRepository extends JpaRepository<SellerInfo, Long> {

    // 이미 셀러 신청/가입 이력이 있는 회원인지 확인 (status 무관 - 중복 신청 방지)
    boolean existsByMemberId(Long memberId);
}
