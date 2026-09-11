package com.wellbuying.domain.settlement.dto;

// 매출 추이 그래프 조회 단위. Postgres date_trunc()의 unit 문자열로 바꿔 집계 쿼리에 그대로 쓴다.
public enum SettlementTrendGranularity {

    WEEKLY("week"),
    MONTHLY("month");

    private final String truncUnit;

    SettlementTrendGranularity(String truncUnit) {
        this.truncUnit = truncUnit;
    }

    public String truncUnit() {
        return truncUnit;
    }
}
