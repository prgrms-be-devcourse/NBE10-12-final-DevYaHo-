package com.wellbuying.domain.settlement.repository;

import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    // 배치 재실행/동시 실행 시 같은 공동구매를 두 번 확정하지 않기 위한 확인용 (UNIQUE 제약과 이중 방어)
    boolean existsByGroupBuyId(Long groupBuyId);

    // 판매자 정산 내역 조회 - 정렬은 서비스가 고정해 넘긴다
    Page<Settlement> findByProducerId(Long producerId, Pageable pageable);

    // 관리자 정산 내역 조회 - 상태 필터 (미지정 시 findAll)
    Page<Settlement> findByStatus(SettlementStatus status, Pageable pageable);
}
