package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.entity.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 이벤트 재수신 시 이미 만들어둔 결제 건을 그대로 이어서 처리하기 위한 조회
    Optional<Payment> findByGroupBuyParticipantId(Long groupBuyParticipantId);

    // PaymentUnconfirmedReconciliationJob이 정리 대상을 훑는 조회 (idx_payment_status_created_at 사용)
    List<Payment> findByStatus(PaymentStatus status);

    // 다중 인스턴스가 같은 UNCONFIRMED 건을 동시에 재시도(실제 승인 호출)하지 않도록 선점한다.
    // 영향받은 row 수가 0이면 이미 다른 인스턴스가 점유했거나 상태가 바뀐 것 - 호출자는 스킵해야 한다
    // (09-pg-timeout-retry.md)
    @Modifying
    @Query("UPDATE Payment p SET p.reconcilingAt = CURRENT_TIMESTAMP "
            + "WHERE p.id = :id AND p.status = com.wellbuying.domain.payment.entity.PaymentStatus.UNCONFIRMED "
            + "AND p.reconcilingAt IS NULL")
    int claimForReconciliation(@Param("id") Long id);

    // 이번 주기에 확정하지 못한 건(조회 실패, 진행 중 상태 등)의 선점을 풀어 다음 주기에 다시 집을 수 있게 한다.
    // APPROVED/FAILED로 확정된 건은 status가 바뀌어 findByStatus(UNCONFIRMED)에 더 이상 안 잡히므로 호출 불필요
    @Modifying
    @Query("UPDATE Payment p SET p.reconcilingAt = NULL WHERE p.id = :id")
    void releaseReconciliationClaim(@Param("id") Long id);
}
