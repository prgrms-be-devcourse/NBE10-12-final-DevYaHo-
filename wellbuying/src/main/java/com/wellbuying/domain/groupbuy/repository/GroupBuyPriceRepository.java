package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.domain.GroupBuyPrice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuyPriceRepository extends JpaRepository<GroupBuyPrice, Long> {

    // 가격 구간 조회 - tierOrder 오름차순
    List<GroupBuyPrice> findByGroupBuyIdOrderByTierOrderAsc(Long groupBuyId);

    // GroupBuyLifecycleScheduler가 한 배치에서 성사 처리할 공동구매 전체의 가격 구간을 한 번의 쿼리로 함께 조회 (N+1 방지)
    List<GroupBuyPrice> findByGroupBuyIdIn(List<Long> groupBuyIds);
}
