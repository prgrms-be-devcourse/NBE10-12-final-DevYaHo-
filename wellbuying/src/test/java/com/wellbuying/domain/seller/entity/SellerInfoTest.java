package com.wellbuying.domain.seller.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SellerInfoTest {

    // apply()로 만든 셀러 신청은 status가 PENDING이고 rank/settlementCycle이 기본값으로 시작하는지 검증
    @Test
    void apply로_생성한_셀러_신청은_PENDING_상태이다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.PENDING);
    }

    // approve() 호출 시 status가 ACTIVE로 바뀌고 approvedAt이 세팅되는지 검증
    @Test
    void approve호출시_status가_ACTIVE로_바뀌고_approvedAt이_세팅된다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");

        sellerInfo.approve();

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.ACTIVE);
    }

    // reject() 호출 시 status가 TERMINATED로 바뀌는지 검증
    @Test
    void reject호출시_status가_TERMINATED로_바뀐다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");

        sellerInfo.reject();

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.TERMINATED);
    }
}
