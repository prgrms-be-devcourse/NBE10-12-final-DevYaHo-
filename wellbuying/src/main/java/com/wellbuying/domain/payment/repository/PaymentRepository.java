package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 이벤트 재수신 시 이미 만들어둔 결제 건을 그대로 이어서 처리하기 위한 조회
    Optional<Payment> findByGroupBuyParticipantId(Long groupBuyParticipantId);
}
