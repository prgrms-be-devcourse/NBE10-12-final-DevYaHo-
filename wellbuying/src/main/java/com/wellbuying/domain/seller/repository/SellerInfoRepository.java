package com.wellbuying.domain.seller.repository;

import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.entity.SellerStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerInfoRepository extends JpaRepository<SellerInfo, Long> {

    // 이미 셀러 신청/가입 이력이 있는 회원인지 확인 (status 무관 - 중복 신청 방지)
    boolean existsByMemberId(Long memberId);

    // 관리자의 상태별 셀러 신청 목록 조회 (예: PENDING 승인 대기 목록)
    List<SellerInfo> findAllByStatus(SellerStatus status);
}
