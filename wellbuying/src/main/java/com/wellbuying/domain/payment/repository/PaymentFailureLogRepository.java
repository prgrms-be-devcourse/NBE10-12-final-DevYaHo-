package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.PaymentFailureLog;
import com.wellbuying.domain.payment.entity.PaymentFailureType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentFailureLogRepository extends JpaRepository<PaymentFailureLog, Long> {

    // 운영자가 확인해야 할 미해결 건 조회
    List<PaymentFailureLog> findByResolvedFalseOrderByCreatedAtAsc();

    // PaymentUnconfirmedReconciliationJob이 UNCONFIRMED 건을 확정한 뒤 해당 로그를 마감할 때 조회
    List<PaymentFailureLog> findByPaymentIdAndFailureTypeAndResolvedFalse(Long paymentId,
            PaymentFailureType failureType);
}
