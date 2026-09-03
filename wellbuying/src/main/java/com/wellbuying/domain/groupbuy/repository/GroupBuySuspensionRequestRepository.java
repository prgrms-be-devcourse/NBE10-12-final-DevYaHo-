package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionRequest;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuySuspensionRequestRepository extends JpaRepository<GroupBuySuspensionRequest, Long> {

    // 관리자의 상태별 판매정지 요청 목록 조회
    Page<GroupBuySuspensionRequest> findAllByStatus(GroupBuySuspensionStatus status, Pageable pageable);

    // 중복 요청 방지 - 같은 공동구매에 이미 처리 대기 중인 요청이 있는지 확인
    boolean existsByGroupBuyIdAndStatus(Long groupBuyId, GroupBuySuspensionStatus status);
}
