package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.PaymentConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentConsumedEventRepository extends JpaRepository<PaymentConsumedEvent, Long> {

    boolean existsByEventId(String eventId);
}
