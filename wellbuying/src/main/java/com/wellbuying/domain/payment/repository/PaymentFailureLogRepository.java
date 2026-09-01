package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.PaymentFailureLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentFailureLogRepository extends JpaRepository<PaymentFailureLog, Long> {

    // 운영자가 확인해야 할 미해결 건 조회
    List<PaymentFailureLog> findByResolvedFalseOrderByCreatedAtAsc();
}
