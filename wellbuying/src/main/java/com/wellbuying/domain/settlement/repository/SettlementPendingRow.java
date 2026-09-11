package com.wellbuying.domain.settlement.repository;

// SettlementItemRepository.findPendingByProducerIdAndFinalizedAtRange()의 네이티브 쿼리 프로젝션 -
// ACCRUED 상태 settlement_item을 group_buy_id로 묶은 "정산 대기중" 미리보기 한 건.
// getter 이름이 SQL 컬럼 별칭과 그대로 맞아야 바인딩된다.
public interface SettlementPendingRow {

    Long getGroupBuyId();

    String getGroupBuyTitle();

    Long getProducerId();

    Long getItemCount();

    Long getTotalSales();
}
