package com.wellbuying.domain.seller.repository;

import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.entity.SellerStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerInfoRepository extends JpaRepository<SellerInfo, Long> {

    // 회원의 셀러 신청 이력 조회 (신청/재신청 중복 체크, 본인 상태 조회 공용 - 회원당 최대 1건)
    Optional<SellerInfo> findByMemberId(Long memberId);

    // 관리자의 상태별 셀러 신청 목록 조회 (예: PENDING 승인 대기 목록)
    Page<SellerInfo> findAllByStatus(SellerStatus status, Pageable pageable);
}
