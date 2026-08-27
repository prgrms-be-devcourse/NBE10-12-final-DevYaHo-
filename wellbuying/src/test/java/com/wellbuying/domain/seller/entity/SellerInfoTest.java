package com.wellbuying.domain.seller.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class SellerInfoTest {

    // apply()로 만든 셀러 신청은 status가 PENDING이고 rank/settlementCycle이 기본값으로 시작하는지 검증
    @Test
    void apply로_생성한_셀러_신청은_PENDING_상태이다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.PENDING);
    }

    // approve() 호출 시 status가 APPROVED로 바뀌고 approvedAt이 세팅되는지 검증
    @Test
    void approve호출시_status가_APPROVED로_바뀌고_approvedAt이_세팅된다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");

        sellerInfo.approve();

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.APPROVED);
    }

    // reject() 호출 시 status가 REJECTED로 바뀌는지 검증
    @Test
    void reject호출시_status가_REJECTED로_바뀐다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");

        sellerInfo.reject();

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.REJECTED);
    }

    // suspend() 호출 시 status가 SUSPENDED로 바뀌는지 검증
    @Test
    void suspend호출시_status가_SUSPENDED로_바뀐다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        sellerInfo.approve();

        sellerInfo.suspend();

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
    }

    // reactivate() 호출 시 status가 다시 APPROVED로 바뀌는지 검증
    @Test
    void reactivate호출시_status가_다시_APPROVED로_바뀐다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        sellerInfo.approve();
        sellerInfo.suspend();

        sellerInfo.reactivate();

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.APPROVED);
    }

    // PENDING이 아닌 상태에서 approve() 호출 시 SELLER_ALREADY_PROCESSED 예외가 발생하는지 검증
    @Test
    void PENDING이_아니면_approve호출시_예외를_던진다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        sellerInfo.approve();

        assertThatThrownBy(sellerInfo::approve)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_ALREADY_PROCESSED);
    }

    // PENDING이 아닌 상태에서 reject() 호출 시 SELLER_ALREADY_PROCESSED 예외가 발생하는지 검증
    @Test
    void PENDING이_아니면_reject호출시_예외를_던진다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        sellerInfo.reject();

        assertThatThrownBy(sellerInfo::reject)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_ALREADY_PROCESSED);
    }

    // APPROVED가 아닌 상태에서 suspend() 호출 시 SELLER_NOT_APPROVED 예외가 발생하는지 검증
    @Test
    void APPROVED가_아니면_suspend호출시_예외를_던진다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");

        assertThatThrownBy(sellerInfo::suspend)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_APPROVED);
    }

    // SUSPENDED가 아닌 상태에서 reactivate() 호출 시 SELLER_NOT_SUSPENDED 예외가 발생하는지 검증
    @Test
    void SUSPENDED가_아니면_reactivate호출시_예외를_던진다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        sellerInfo.approve();

        assertThatThrownBy(sellerInfo::reactivate)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELLER_NOT_SUSPENDED);
    }

    // PENDING/REJECTED 상태는 탈퇴 시 삭제 대상이고, APPROVED/SUSPENDED는 삭제 대상이 아닌지 검증
    @Test
    void isDeletableOnWithdraw는_PENDING과_REJECTED만_true를_반환한다() {
        SellerInfo pending = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        SellerInfo rejected = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        rejected.reject();
        SellerInfo approved = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        approved.approve();
        SellerInfo suspended = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        suspended.approve();
        suspended.suspend();

        assertThat(pending.isDeletableOnWithdraw()).isTrue();
        assertThat(rejected.isDeletableOnWithdraw()).isTrue();
        assertThat(approved.isDeletableOnWithdraw()).isFalse();
        assertThat(suspended.isDeletableOnWithdraw()).isFalse();
    }

    // reapply() 호출 시 status가 PENDING으로 되돌아가고 신청 정보가 새 값으로 갱신되는지 검증
    @Test
    void reapply호출시_status가_PENDING으로_되돌아가고_정보가_갱신된다() {
        SellerInfo sellerInfo = SellerInfo.apply(1L, "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어");
        sellerInfo.reject();

        sellerInfo.reapply("004", "국민은행", "110-987-654321", "김철수", "웰바잉스토어2");

        assertThat(sellerInfo.getStatus()).isEqualTo(SellerStatus.PENDING);
        assertThat(sellerInfo.getBankName()).isEqualTo("국민은행");
        assertThat(sellerInfo.getCompanyName()).isEqualTo("웰바잉스토어2");
    }
}
