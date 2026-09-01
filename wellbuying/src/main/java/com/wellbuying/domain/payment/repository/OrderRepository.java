package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Payment는 승인됐는데 Order 생성만 실패한 건을 재처리할 때, 이미 만들어졌는지 확인
    boolean existsByPaymentId(Long paymentId);
}
