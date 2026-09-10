package com.wellbuying.domain.settlement.repository;

import com.wellbuying.domain.settlement.entity.SettlementItem;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    // Kafka 재수신으로 같은 결제 완료 이벤트가 다시 소비돼도 중복 적립하지 않기 위한 사전 확인용
    boolean existsByGroupBuyParticipantId(Long groupBuyParticipantId);

    // 이미 확정된 공동구매에 뒤늦게 결제 완료가 도착하는 경우를 걸러내기 위한 확인용
    boolean existsByGroupBuyIdAndStatus(Long groupBuyId, SettlementItemStatus status);
}
