package com.wellbuying.domain.settlement.repository;

import java.time.LocalDateTime;

// SettlementItemRepository.findTrend()의 네이티브 쿼리 결과를 받는 인터페이스 프로젝션.
// getter 이름이 SQL의 컬럼 별칭(periodStart/totalSales/groupBuyCount)과 그대로 맞아야 바인딩된다.
public interface SettlementTrendRow {

    LocalDateTime getPeriodStart();

    Long getTotalSales();

    Long getGroupBuyCount();
}
