package com.wellbuying.domain.settlement.repository;

import com.wellbuying.domain.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    // 배치 재실행/동시 실행 시 같은 공동구매를 두 번 확정하지 않기 위한 확인용 (UNIQUE 제약과 이중 방어)
    boolean existsByGroupBuyId(Long groupBuyId);
}
